package httpserver.itf.impl;

import java.util.concurrent.ConcurrentHashMap;

import httpserver.itf.HttpSession;

class HttpSessionImpl implements HttpSession {
	private final String m_id;
	private final ConcurrentHashMap<String, Object> m_values = new ConcurrentHashMap<>();
	private volatile long m_lastAccessMs;

	HttpSessionImpl(String id) {
		this.m_id = id;
		this.m_lastAccessMs = System.currentTimeMillis();
	}

	@Override
	public String getId() {
		return m_id;
	}

	@Override
	public Object getValue(String key) {
		return m_values.get(key);
	}

	@Override
	public void setValue(String key, Object value) {
		m_values.put(key, value);
	}

	void touch(long nowMs) {
		m_lastAccessMs = nowMs;
	}

	boolean isExpired(long nowMs, long timeoutMs) {
		return timeoutMs >= 0 && (nowMs - m_lastAccessMs) > timeoutMs;
	}
}
