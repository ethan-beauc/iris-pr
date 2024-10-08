/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2024  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.sonar.server;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.Props;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;
import us.mn.state.dot.tms.server.AccessLogger;
import us.mn.state.dot.tms.server.HashProvider;

/**
 * The SONAR server processes all data transfers with client connections.
 *
 * @author Douglas Lau
 */
public final class Server {

	/** Selector thread */
	private final SelectorThread thread;

	/** Task processor */
	private final TaskProcessor processor;

	/** Create the SONAR server */
	public Server(ServerNamespace n, Properties props,
		AccessLogger al, HashProvider hp) throws IOException,
		ConfigurationError
	{
		int port = Props.getIntProp(props, "sonar.port");
		processor = new TaskProcessor(n, props, al, hp);
		thread = new SelectorThread(processor, port);
	}

	/** Join the selector thread */
	public void join() throws InterruptedException {
		thread.join();
	}

	/** Add the specified object to the server's namespace */
	public void addObject(SonarObject o) {
		processor.scheduleAddObject(o);
	}

	/** Create (synchronously) an object in the server's namespace */
	public void createObject(SonarObject o) throws SonarException {
		processor.storeObject(o);
	}

	/** Remove the specified object from the server's namespace */
	public void removeObject(SonarObject o) {
		processor.scheduleRemoveObject(o);
	}

	/** Set the specified attribute in the server's namespace */
	public void setAttribute(SonarObject o, String a) {
		processor.scheduleSetAttribute(o, a);
	}

	/** Get user for current message processing */
	public String getProcUser() {
		ConnectionImpl c = processor.getProcConnection();
		return (c != null) ? c.getUserName() : null;
	}

	/** Get address for current message processing */
	public InetAddress getProcAddress() {
		ConnectionImpl c = processor.getProcConnection();
		return (c != null) ? c.getAddress() : null;
	}
}
