package httpserver.itf.impl;

import java.io.IOException;


import httpserver.itf.HttpRequest;
import httpserver.itf.HttpResponse;

/*
 * This class allows to build an object representing an HTTP static request
 */
public class HttpStaticRequest extends HttpRequest {
	static final String DEFAULT_FILE = "index.html";
	
	public HttpStaticRequest(HttpServer hs, String method, String ressname) throws IOException {
		super(hs, method, ressname);
	}
	
	public void process(HttpResponse resp) throws Exception {

	    String filename = m_ressname;

	
	    if (filename.equals("/")) {
	        filename = "/" + DEFAULT_FILE;
	    }

	 
	    if (filename.startsWith("/")) {
	        filename = filename.substring(1);
	    }

	    // Construire le chemin complet
	    String fullpath = m_hs.getFolder() + "/" + filename;

	    java.io.File file = new java.io.File(fullpath);

	    // Si fichier existe
	    if (file.exists() && file.isFile()) {

	        // Start-line OK
	        resp.setReplyOk();

	        // Headers
	        resp.setContentType(HttpRequest.getContentType(filename));
	        resp.setContentLength((int) file.length());

	        // Début du body
	        java.io.PrintStream out = resp.beginBody();

	        // Lire et envoyer le fichier
	        java.io.FileInputStream fis = new java.io.FileInputStream(file);

	        byte[] buffer = new byte[1024];
	        int bytesRead;

	        while ((bytesRead = fis.read(buffer)) != -1) {
	            out.write(buffer, 0, bytesRead);
	        }

	        fis.close();

	    } else {
	        // 404 - setReplyError s'occupe de générer toute la réponse HTTP (en-têtes + page HTML d'erreur)
	        resp.setReplyError(404, "File not found");
	    }
	}

}
