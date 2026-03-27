package httpserver.itf.impl;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import httpserver.itf.HttpRequest;
import httpserver.itf.HttpRicmletResponse;

class HttpRicmletResponseImpl extends HttpResponseImpl implements HttpRicmletResponse {
	private final List<String> m_cookies = new ArrayList<>();
	private boolean m_replyStarted = false;

	protected HttpRicmletResponseImpl(HttpServer hs, HttpRequest req, PrintStream ps) {
		super(hs, req, ps);
	}

	@Override
	public void setCookie(String name, String value) {
		String cookie = name + "=" + value + "; path=/";
		if (m_replyStarted) {
			m_ps.println("Set-Cookie: " + cookie);
		} else {
			m_cookies.add(cookie);
		}
	}

	@Override
	public void setReplyOk() {
		m_replyStarted = true;
		super.setReplyOk();
		for (String cookie : m_cookies) {
			m_ps.println("Set-Cookie: " + cookie);
		}
		m_cookies.clear();
	}

	@Override
	public void setReplyError(int codeRet, String msg) throws IOException {
		m_replyStarted = true;
		m_ps.println("HTTP/1.0 " + codeRet + " " + msg);
		m_ps.println("Date: " + new Date());
		m_ps.println("Server: ricm-http 1.0");
		m_ps.println("Content-type: text/html");
		for (String cookie : m_cookies) {
			m_ps.println("Set-Cookie: " + cookie);
		}
		m_cookies.clear();
		m_ps.println();
		m_ps.println("<HTML><HEAD><TITLE>" + msg + "</TITLE></HEAD>");
		m_ps.println("<BODY><H4>HTTP Error " + codeRet + ": " + msg + "</H4></BODY></HTML>");
		m_ps.flush();
	}
}