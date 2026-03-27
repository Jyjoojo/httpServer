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

	private final Map<String, String> m_args = new HashMap<>();
	private final List<String> m_candidateRicmletClassNames = new ArrayList<>();

	HttpRicmletRequestImpl(HttpServer hs, String method, String ressname, BufferedReader br) throws IOException {
		super(hs, method, ressname, br);
		parseArgsAndRicmletTarget(ressname);
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

		String asClass = p.replace('/', '.');
		m_candidateRicmletClassNames.clear();
		m_candidateRicmletClassNames.add(asClass);
	}

	private static String urlDecode(String value) {
		if (value == null) return null;
		try {
			return URLDecoder.decode(value, "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			return value;
		}
	}

	@Override
	public HttpSession getSession() {
		return null;
	}

	@Override
	public String getArg(String name) {
		return m_args.get(name);
	}

	@Override
	public String getCookie(String name) {
		return null;
	}

	@Override
	public void process(HttpResponse resp) throws Exception {
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