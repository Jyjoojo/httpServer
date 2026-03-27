package httpserver.itf.impl;

import java.io.PrintStream;
import httpserver.itf.HttpRequest;
import httpserver.itf.HttpRicmletResponse;

class HttpRicmletResponseImpl extends HttpResponseImpl implements HttpRicmletResponse {
	protected HttpRicmletResponseImpl(HttpServer hs, HttpRequest req, PrintStream ps) {
		super(hs, req, ps);
	}

	@Override
	public void setCookie(String name, String value) {
		// Pas encore
	}
}