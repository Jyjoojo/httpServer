package httpserver.itf.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import httpserver.itf.HttpRicmlet;
import httpserver.itf.HttpRicmletRequest;
import httpserver.itf.HttpRicmletResponse;
import httpserver.itf.HttpResponse;
import httpserver.itf.HttpSession;

class HttpRicmletRequestImpl extends HttpRicmletRequest {
	private static final String SESSION_COOKIE_NAME = "session-id";

	private final Map<String, String> m_args = new HashMap<>();
	private final Map<String, String> m_cookies = new HashMap<>();
	private final List<String> m_candidateRicmletClassNames = new ArrayList<>();

	private HttpRicmletResponse m_responseForSessionCookies;
	private boolean m_sessionCookieSent = false;

	HttpRicmletRequestImpl(HttpServer hs, String method, String ressname, String cookieHeader, BufferedReader br) throws IOException {
		super(hs, method, ressname, br);
		parseArgsAndRicmletTarget(ressname);
		parseCookies(cookieHeader);
	}

	private void parseArgsAndRicmletTarget(String ressname) {
		String[] parts = ressname.split("\\?", 2);
		String pathPart = parts[0];
		String queryPart = parts.length > 1 ? parts[1] : "";

		parseQueryArgs(queryPart);
		parseRicmletClassCandidates(pathPart);
	}

	private void parseQueryArgs(String queryPart) {
		if (queryPart == null || queryPart.isEmpty()) return;
		String[] pairs = queryPart.split("&");
		for (String pair : pairs) {
			if (pair == null || pair.isEmpty()) continue;
			int eqIdx = pair.indexOf('=');
			String rawKey = eqIdx >= 0 ? pair.substring(0, eqIdx) : pair;
			String rawVal = eqIdx >= 0 ? pair.substring(eqIdx + 1) : "";
			String key = urlDecode(rawKey);
			String val = urlDecode(rawVal);
			m_args.put(key, val);
		}
	}

	private void parseRicmletClassCandidates(String pathPart) {
		String p = pathPart;
		if (p.startsWith("/")) p = p.substring(1);
		if (p.startsWith("ricmlets/")) p = p.substring("ricmlets/".length());
		if (p.equals("ricmlets")) p = "";
		if (p.isEmpty()) return;

		// STEP2 URL scheme: /ricmlets/<package>/<RicmletClassName>
		// Example: /ricmlets/examples/HelloRicmlet -> examples.HelloRicmlet
		String asClass = p.replace('/', '.');
		m_candidateRicmletClassNames.clear();
		m_candidateRicmletClassNames.add(asClass);
	}

	private void parseCookies(String cookieHeader) {
		if (cookieHeader == null || cookieHeader.isEmpty()) return;
		String[] parts = cookieHeader.split(";");
		for (String part : parts) {
			String trimmed = part.trim();
			if (trimmed.isEmpty()) continue;
			int eqIdx = trimmed.indexOf('=');
			if (eqIdx <= 0) continue;
			String name = trimmed.substring(0, eqIdx).trim();
			String value = trimmed.substring(eqIdx + 1).trim();
			m_cookies.put(name, value);
		}
	}

	private static String urlDecode(String value) {
		if (value == null) return null;
		// Java 8 does not support URLDecoder#decode(String, Charset) in all environments.
		try {
			return URLDecoder.decode(value, "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			// Should never happen since UTF-8 is always supported.
			return value;
		}
	}

	@Override
	public HttpSession getSession() {
		String sessionId = getCookie(SESSION_COOKIE_NAME);
		boolean needToSetCookie = false;

		HttpSession s = m_hs.getSession(sessionId);
		if (sessionId == null || s == null) {
			needToSetCookie = true;
			if (sessionId == null) sessionId = m_hs.newSessionId();
			s = m_hs.createSession(sessionId);
		}

		if (needToSetCookie && !m_sessionCookieSent && m_responseForSessionCookies != null) {
			m_responseForSessionCookies.setCookie(SESSION_COOKIE_NAME, sessionId);
			m_sessionCookieSent = true;
		}

		return s;
	}

	@Override
	public String getArg(String name) {
		return m_args.get(name);
	}

	@Override
	public String getCookie(String name) {
		return m_cookies.get(name);
	}

	@Override
	public void process(HttpResponse resp) throws Exception {
		m_responseForSessionCookies = (HttpRicmletResponse) resp;
		HttpRicmlet ricmlet = null;
		for (String cls : m_candidateRicmletClassNames) {
			try {
				ricmlet = m_hs.getInstance(cls);
				break;
			} catch (Exception e) {
			}
		}
		if (ricmlet == null) {
			resp.setReplyError(404, "Ricmlet not found");
			return;
		}
		ricmlet.doGet(this, (HttpRicmletResponse) resp);
	}
}

