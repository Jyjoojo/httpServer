package examples;

import java.io.IOException;
import java.io.PrintStream;

import httpserver.itf.HttpRicmletRequest;
import httpserver.itf.HttpRicmletResponse;
import httpserver.itf.HttpSession;

public class SessionRicmlet implements httpserver.itf.HttpRicmlet {
	@Override
	public void doGet(HttpRicmletRequest req, HttpRicmletResponse resp) throws IOException {
		HttpSession s = req.getSession();

		resp.setReplyOk();
		resp.setContentType("text/html");
		PrintStream ps = resp.beginBody();
		ps.println("<html><body>");
		ps.println("<h4>session-id cookie = " + req.getCookie("session-id") + "</h4>");
		ps.println("<h4>session = " + s + "</h4>");
		ps.println("</body></html>");
	}
}

