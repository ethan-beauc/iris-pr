/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2009-2024  Minnesota Department of Transportation
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
package us.mn.state.dot.tms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Helper class for device actions.
 *
 * @author Douglas Lau
 */
public class DeviceActionHelper extends BaseHelper {

	/** Don't allow instances to be created */
	private DeviceActionHelper() {
		assert false;
	}

	/** Lookup the device action with the specified name */
	static public DeviceAction lookup(String name) {
		return (DeviceAction) namespace.lookupObject(
			DeviceAction.SONAR_TYPE, name);
	}

	/** Get a device action iterator */
	static public Iterator<DeviceAction> iterator() {
		return new IteratorWrapper<DeviceAction>(namespace.iterator(
			DeviceAction.SONAR_TYPE));
	}

	/** Get set of hashtags with an action using a given message pattern */
	static public Set<String> findHashtags(MsgPattern pat) {
		HashSet<String> hashtags = new HashSet<String>();
		Iterator<DeviceAction> it = iterator();
		while (it.hasNext()) {
			DeviceAction da = it.next();
			if (da.getMsgPattern() == pat)
				hashtags.add(da.getHashtag());
		}
		return hashtags;
	}

	/** Find all device actions for a set of hashtags */
	static public ArrayList<DeviceAction> find(Hashtags tags) {
		ArrayList<DeviceAction> actions = new ArrayList<DeviceAction>();
		Iterator<DeviceAction> it = iterator();
		while (it.hasNext()) {
			DeviceAction da = it.next();
			if (tags.contains(da.getHashtag()))
				actions.add(da);
		}
		return actions;
	}
}
