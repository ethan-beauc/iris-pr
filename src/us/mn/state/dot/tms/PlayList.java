/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2017-2024  Minnesota Department of Transportation
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

import us.mn.state.dot.sonar.SonarObject;

/**
 * Play list (camera sequence).
 *
 * @author Douglas Lau
 */
public interface PlayList extends SonarObject {

	/** Minimum sequence number */
	int NUM_MIN = 1;

	/** Maximum sequence number */
	int NUM_MAX = 9999;

	/** SONAR type name */
	String SONAR_TYPE = "play_list";

	/** Get the SONAR type name */
	@Override
	default String getTypeName() {
		return SONAR_TYPE;
	}

	/** SONAR base type name */
	String SONAR_BASE = Camera.SONAR_TYPE;

	/** Set sequence number */
	void setSeqNum(Integer n);

	/** Get sequence number */
	Integer getSeqNum();

	/** Set description */
	void setDescription(String d);

	/** Get description */
	String getDescription();

	/** Set the cameras in the play list */
	void setCameras(Camera[] cams);

	/** Get the cameras in the play list */
	Camera[] getCameras();
}
