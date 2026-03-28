package httpserver.itf.impl;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import httpserver.itf.HttpRequest;
import httpserver.itf.HttpResponse;
import httpserver.itf.HttpRicmlet;
import httpserver.itf.HttpRicmletRequest;


/**
 * Basic HTTP Server Implementation 
 * 
 * Only manages static requests
 * The url for a static ressource is of the form: "http//host:port/<path>/<ressource name>"
 * For example, try accessing the following urls from your brower:
 *    http://localhost:<port>/
 *    http://localhost:<port>/voile.jpg
 *    ...
 */
public class HttpServer {

	private int m_port;
	private File m_folder;  // default folder for accessing static resources (files)
	private ServerSocket m_ssoc;
	// In-memory session store, shared across requests.
	private final ConcurrentHashMap<String, HttpSessionImpl> m_sessions = new ConcurrentHashMap<>();
	// Ricmlet singleton lifecycle: max one instance per ricmlet class.
	private final ConcurrentHashMap<String, HttpRicmlet> m_ricmletInstances = new ConcurrentHashMap<>();

	protected HttpServer(int port, String folderName) {
		m_port = port;
		if (!folderName.endsWith(File.separator)) 
			folderName = folderName + File.separator;
		m_folder = new File(folderName);
		try {
			m_ssoc=new ServerSocket(m_port);
			System.out.println("HttpServer started on port " + m_port);
		} catch (IOException e) {
			System.out.println("HttpServer Exception:" + e );
			System.exit(1);
		}
	}
	
	public File getFolder() {
		return m_folder;
	}
	
	

	public HttpRicmlet getInstance(String clsname)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, MalformedURLException, 
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		HttpRicmlet existing = m_ricmletInstances.get(clsname);
		if (existing != null) return existing;

		Class<?> c = Class.forName(clsname);
		if (!HttpRicmlet.class.isAssignableFrom(c)) {
			throw new IllegalArgumentException("Class does not implement HttpRicmlet: " + clsname);
		}

		try {
			HttpRicmlet created = (HttpRicmlet) c.getDeclaredConstructor().newInstance();
			HttpRicmlet previous = m_ricmletInstances.putIfAbsent(clsname, created);
			return previous != null ? previous : created;
		} catch (ReflectiveOperationException e) {
			throw new InvocationTargetException(e);
		}
	}




	/*
	 * Reads a request on the given input stream and returns the corresponding HttpRequest object
	 */
	public HttpRequest getRequest(BufferedReader br) throws IOException {
		HttpRequest request = null;
		
		String startline = br.readLine();
		if (startline == null) {
			throw new IOException("Empty HTTP request");
		}
		StringTokenizer parseline = new StringTokenizer(startline);
		String method = parseline.nextToken().toUpperCase(); 
		String ressname = parseline.nextToken();
		String pathOnly = ressname;
		int qmarkIdx = ressname.indexOf('?');
		if (qmarkIdx >= 0) pathOnly = ressname.substring(0, qmarkIdx);

		Map<String, String> headers = readHeaders(br);
		String cookieHeader = headers.get("cookie");

		if (method.equals("GET")) {
			if (isRicmletRequest(pathOnly)) {
				request = new HttpRicmletRequestImpl(this, method, ressname, cookieHeader, br);
			} else {
				// Static requests should not include query string in filename lookup.
				request = new HttpStaticRequest(this, method, pathOnly);
			}
		} else 
			request = new UnknownRequest(this, method, ressname);
		return request;
	}


	/*
	 * Returns an HttpResponse object associated to the given HttpRequest object
	 */
	public HttpResponse getResponse(HttpRequest req, PrintStream ps) {
		if (req instanceof HttpRicmletRequest) {
			return new HttpRicmletResponseImpl(this, req, ps);
		}
		return new HttpResponseImpl(this, req, ps);
	}


	/*
	 * Server main loop
	 */
	protected void loop() {
		try {
			while (true) {
				Socket soc = m_ssoc.accept();
				(new HttpWorker(this, soc)).start();
			}
		} catch (IOException e) {
			System.out.println("HttpServer Exception, skipping request");
			e.printStackTrace();
		}
	}


	// Consume HTTP headers until the blank line.
	private Map<String, String> readHeaders(BufferedReader br) throws IOException {
		Map<String, String> headers = new HashMap<>();
		String line;
		while ((line = br.readLine()) != null) {
			if (line.isEmpty()) break;
			int colonIdx = line.indexOf(':');
			if (colonIdx <= 0) continue;
			String name = line.substring(0, colonIdx).trim().toLowerCase();
			String value = line.substring(colonIdx + 1).trim();
			headers.put(name, value);
		}
		return headers;
	}

	private static boolean isStaticResource(String pathOnly) {
		if (pathOnly == null || pathOnly.isEmpty() || pathOnly.equals("/")) return true;
		String p = pathOnly;
		if (p.startsWith("/")) p = p.substring(1);
		String name = p;
		int slashIdx = p.lastIndexOf('/');
		if (slashIdx >= 0) name = p.substring(slashIdx + 1);
		String lower = name.toLowerCase();
		// Keep the list aligned with HttpRequest#getContentType.
		return lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".txt") || lower.endsWith(".gif")
				|| lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".pdf")
				|| lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".class") || lower.endsWith(".java");
	}

	private static boolean isRicmletRequest(String pathOnly) {
		if (pathOnly == null) return false;
		return pathOnly.startsWith("/ricmlets") || pathOnly.equals("ricmlets");
	}

	HttpSessionImpl getSession(String id) {
		if (id == null) return null;
		HttpSessionImpl s = m_sessions.get(id);
		if (s == null) return null;
		// Lazy destruction on access: if the session was idle for too long, remove it.
		long now = System.currentTimeMillis();
		if (s.isExpired(now, SESSION_TIMEOUT_MS)) {
			m_sessions.remove(id);
			return null;
		}
		s.touch(now);
		return s;
	}

	String newSessionId() {
		return UUID.randomUUID().toString();
	}

	HttpSessionImpl createSession(String id) {
		HttpSessionImpl session = new HttpSessionImpl(id);
		HttpSessionImpl previous = m_sessions.putIfAbsent(id, session);
		return previous != null ? previous : session;
	}

	// Session inactivity timeout (STEP4). The PDF does not specify a value, so we keep a reasonable default.
	private static final long SESSION_TIMEOUT_MS = 10L * 60L * 1000L;

	public static void main(String[] args) {
		int port = 0;
		if (args.length != 2) {
			System.out.println("Usage: java Server <port-number> <file folder>");
		} else {
			port = Integer.parseInt(args[0]);
			String foldername = args[1];
			HttpServer hs = new HttpServer(port, foldername);
			hs.loop();
		}
	}

}
