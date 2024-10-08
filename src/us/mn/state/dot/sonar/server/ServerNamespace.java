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
import java.util.Iterator;
import java.util.HashMap;
import us.mn.state.dot.sonar.EmptyIterator;
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.MessageEncoder;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;
import us.mn.state.dot.tms.User;

/**
 * A SONAR namespace is a mapping from all SONAR names to types, objects and
 * attributes.
 *
 * @author Douglas Lau
 */
public class ServerNamespace extends Namespace {

	/** All SONAR types are stored in the root of the namespace */
	private final HashMap<String, TypeNode> root =
		new HashMap<String, TypeNode>();

	/** Register a new type in the namespace */
	private TypeNode registerType(SonarObject o) throws SonarException {
		return registerType(o.getClass());
	}

	/** Get a type node from the namespace */
	private TypeNode _getTypeNode(String t) {
		synchronized (root) {
			return root.get(t);
		}
	}

	/** Get a type node from the namespace */
	private TypeNode getTypeNode(SonarObject o) throws SonarException {
		TypeNode n = _getTypeNode(o.getTypeName());
		return (n != null) ? n : registerType(o);
	}

	/** Get a type node from the namespace by name */
	private TypeNode getTypeNode(Name name) throws NamespaceError {
		TypeNode t = _getTypeNode(name.getTypePart());
		if (t == null)
			throw NamespaceError.nameUnknown(name.toString());
		else
			return t;
	}

	/** Set the value of an attribute.
	 * @param name Attribute name in SONAR namespace.
	 * @param v New attribute value.
	 * @return phantom object if one was created; null otherwise */
	SonarObject setAttribute(Name name, String[] v) throws SonarException {
		TypeNode t = getTypeNode(name);
		return t.setValue(name, v);
	}

	/** Set the value of an attribute on a phantom object.
	 * @param name Attribute name in SONAR namespace.
	 * @param v New attribute value.
	 * @param phantom Phantom object to set attribute on. */
	void setAttribute(Name name, String[] v, SonarObject phantom)
		throws SonarException
	{
		TypeNode t = getTypeNode(name);
		t.setField(phantom, name.getAttributePart(), v);
	}

	/** Test if an attribute is gettable */
	boolean isGettable(Name name) {
		try {
			TypeNode t = getTypeNode(name);
			return t.isGettable(name.getAttributePart());
		}
		catch (NamespaceError e) {
			// Unregistered type
			return false;
		}
	}

	/** Get the value of an attribute */
	String[] getAttribute(Name name) throws SonarException {
		TypeNode t = getTypeNode(name);
		SonarObject o = t.lookupObject(name.getObjectPart());
		if (o != null)
			return t.getValue(o, name.getAttributePart());
		else
			throw NamespaceError.nameInvalid(name);
	}

	/** Remove an object from the namespace */
	void removeObject(SonarObject o) throws SonarException {
		TypeNode n = getTypeNode(o);
		n.removeObject(o);
	}

	/** Lookup the object with the specified name */
	SonarObject lookupObject(Name name) {
		return name.isObject()
		      ?	_lookupObject(name.getTypePart(), name.getObjectPart())
		      : null;
	}

	/** Enumerate the root of the namespace */
	private void enumerateRoot(MessageEncoder enc) throws IOException {
		synchronized (root) {
			for (TypeNode t: root.values())
				enc.encode(Message.TYPE, t.tname);
		}
		enc.encode(Message.TYPE);
	}

	/** Enumerate all objects of the named type */
	private void enumerateType(MessageEncoder enc, Name name)
		throws SonarException, IOException
	{
		TypeNode t = getTypeNode(name);
		enc.encode(Message.TYPE, name.getTypePart());
		t.enumerateObjects(enc);
		enc.encode(Message.TYPE);
	}

	/** Enumerate all attributes of the named object */
	void enumerateObject(MessageEncoder enc, SonarObject o)
		throws SonarException, IOException
	{
		TypeNode t = getTypeNode(o);
		t.enumerateObject(enc, o);
	}

	/** Enumerate all attributes of the named object */
	private void enumerateObject(MessageEncoder enc, Name name)
		throws SonarException, IOException
	{
		SonarObject o = lookupObject(name);
		if (o != null)
			enumerateObject(enc, o);
		else
			throw NamespaceError.nameInvalid(name);
	}

	/** Enumerate everything contained by a name in the namespace */
	void enumerate(MessageEncoder enc, Name name) throws SonarException,
		IOException
	{
		if (name.isRoot())
			enumerateRoot(enc);
		else if (name.isType())
			enumerateType(enc, name);
		else if (name.isObject())
			enumerateObject(enc, name);
		else
			throw NamespaceError.nameInvalid(name);
	}

	/** Register a new type in the namespace.
	 * @param c Type class.
	 * @return New type node. */
	public TypeNode registerType(Class c) throws SonarException {
		try {
			TypeNode node = new TypeNode(this, c);
			synchronized (root) {
				root.put(node.tname, node);
			}
			return node;
		}
		catch (NoSuchFieldException | IllegalAccessException e) {
			throw new SonarException(e);
		}
	}

	/** Add an object into the namespace without storing */
	public void addObject(SonarObject o) throws SonarException {
		getTypeNode(o).addObject(o);
	}

	/** Store an object in the namespace */
	public void storeObject(SonarObject o) throws SonarException {
		getTypeNode(o).storeObject(o);
	}

	/** Create a new object */
	public SonarObject createObject(Name name) throws SonarException {
		TypeNode n = getTypeNode(name);
		return n.createObject(name.getObjectPart());
	}

	/** Get base resource type name */
	@Override
	protected String getTypeBase(Name name) {
		TypeNode t = _getTypeNode(name.getTypePart());
		return (t != null) ? t.base : null;
	}

	/** Lookup an object in the SONAR namespace.
	 * @param tname Sonar type name.
	 * @param oname Sonar object name (may be null).
	 * @return Object from namespace or null if name does not exist */
	@Override
	public SonarObject lookupObject(String tname, String oname) {
		return (oname != null) ? _lookupObject(tname, oname) : null;
	}

	/** Lookup an object in the SONAR namespace.
	 * @param tname Sonar type name.
	 * @param oname Sonar object name (may not be null).
	 * @return Object from namespace or null if name does not exist */
	private SonarObject _lookupObject(String tname, String oname) {
		assert oname != null;
		TypeNode t = _getTypeNode(tname);
		return (t != null) ? t.lookupObject(oname) : null;
	}

	/** Get an iterator for all objects of a type.
	 * @param tname Sonar type name.
	 * @return Iterator of all objects of the type. */
	@Override
	public Iterator<SonarObject> iterator(String tname) {
		TypeNode t = _getTypeNode(tname);
		return (t != null) ? t.iterator() : new EmptyIterator();
	}

	/** Get a count of the number of objects of the specified type.
	 * @param tname Sonar type name
	 * @return Total number of objects of the specified type */
	@Override
	public int getCount(String tname) {
		TypeNode t = _getTypeNode(tname);
		return (t != null) ? t.size() : 0;
	}
}
