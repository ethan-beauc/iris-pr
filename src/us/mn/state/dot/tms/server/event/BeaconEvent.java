/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2014-2022  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server.event;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import us.mn.state.dot.tms.BeaconState;
import static us.mn.state.dot.tms.EventType.BEACON_EVENT;
import us.mn.state.dot.tms.SystemAttrEnum;
import us.mn.state.dot.tms.TMSException;

/**
 * This is a class for logging beacon events to a database.
 *
 * @author Douglas Lau
 */
public class BeaconEvent extends BaseEvent {

	/** Database table name */
	static private final String TABLE = "event.beacon_event";

	/** Get beacon event purge threshold (days) */
	static private int getPurgeDays() {
		return SystemAttrEnum.BEACON_EVENT_PURGE_DAYS.getInt();
	}

	/** Purge old records */
	static public void purgeRecords() throws TMSException {
		int age = getPurgeDays();
		if (store != null && age > 0) {
			store.update("DELETE FROM " + TABLE +
				" WHERE event_date < now() - '" + age +
				" days'::interval;");
		}
	}

	/** Beacon ID */
	private final String beacon;

	/** Beacon state ordinal */
	private final int state;

	/** Create a new beacon event */
	public BeaconEvent(String bid, BeaconState bs) {
		super(BEACON_EVENT);
		beacon = bid;
		state = bs.ordinal();
	}

	/** Get the database table name */
	@Override
	public String getTable() {
		return TABLE;
	}

	/** Get a mapping of the columns */
	@Override
	public Map<String, Object> getColumns() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("event_date", new Timestamp(event_date.getTime()));
		map.put("beacon", beacon);
		map.put("state", state);
		return map;
	}
}
