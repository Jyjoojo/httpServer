package httpserver.itf.impl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import httpserver.itf.HttpRicmlet;

/**
 * Represents an application backed by a jar file.
 * It owns a dedicated classloader and provides singleton ricmlet instances per class.
 */
class Application {
	private final String m_appName;
	private final URLClassLoader m_appClassLoader;
	private final ConcurrentHashMap<String, HttpRicmlet> m_instances = new ConcurrentHashMap<>();
	// Index from simple class name -> fully qualified class name.
	private final Map<String, String> m_simpleNameToFqcn;

	Application(String appName, File appJarFile, ClassLoader parent) throws MalformedURLException, IOException {
		m_appName = appName;
		m_appClassLoader = new URLClassLoader(new URL[] { appJarFile.toURI().toURL() }, parent);
		m_simpleNameToFqcn = buildRicmletIndex(appJarFile);
	}

	HttpRicmlet getRicmletInstance(String className) throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String resolved = resolveClassName(className);
		HttpRicmlet existing = m_instances.get(resolved);
		if (existing != null) return existing;

		Class<?> c;
		try {
			c = Class.forName(resolved, true, m_appClassLoader);
		} catch (ClassNotFoundException e) {
			// Fallback: sometimes className is already FQCN but not indexed.
			c = m_appClassLoader.loadClass(resolved);
		}

		if (!HttpRicmlet.class.isAssignableFrom(c)) {
			throw new IllegalArgumentException("Class does not implement HttpRicmlet: " + resolved + " (app=" + m_appName + ")");
		}

		try {
			HttpRicmlet created = (HttpRicmlet) c.getDeclaredConstructor().newInstance();
			synchronized (m_instances) {
				HttpRicmlet raced = m_instances.get(resolved);
				if (raced != null) return raced;
				m_instances.put(resolved, created);
				return created;
			}
		} catch (ReflectiveOperationException e) {
			throw new InvocationTargetException(e);
		}
	}

	private String resolveClassName(String className) throws ClassNotFoundException {
		if (className == null || className.isEmpty()) throw new ClassNotFoundException("Empty ricmlet class name");
		// If already fully qualified, use it directly.
		if (className.indexOf('.') >= 0) return className;
		String fqcn = m_simpleNameToFqcn.get(className);
		if (fqcn != null) return fqcn;
		// Default package fallback: allow classes without a package in the jar.
		return className;
	}

	private static Map<String, String> buildRicmletIndex(File jarFile) throws IOException {
		Map<String, String> index = new HashMap<>();
		try (JarFile jar = new JarFile(jarFile)) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				String name = e.getName();
				if (!name.endsWith(".class")) continue;
				// Skip inner classes.
				if (name.indexOf('$') >= 0) continue;
				String fqcn = name.substring(0, name.length() - ".class".length()).replace('/', '.');
				int lastDot = fqcn.lastIndexOf('.');
				String simple = lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
				// If duplicates exist, keep the first one; user can call by FQCN to disambiguate.
				if (!index.containsKey(simple)) {
					index.put(simple, fqcn);
				}
			}
		}
		return index;
	}
}

