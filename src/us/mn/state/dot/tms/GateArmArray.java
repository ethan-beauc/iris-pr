/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2013-2024  Minnesota Department of Transportation
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

/**
 * Gate Arm Array interface.
 *
 * @author Douglas Lau
 */
public interface GateArmArray extends Device {

	/** SONAR type name */
	String SONAR_TYPE = "gate_arm_array";

	/** Get the SONAR type name */
	@Override
	default String getTypeName() {
		return SONAR_TYPE;
	}

	/** SONAR base type name */
	String SONAR_BASE = GateArm.SONAR_TYPE;

	/** Maximum number of gate arms in array */
	int MAX_ARMS = 8;

	/** Get the device location */
	GeoLoc getGeoLoc();

	/** Set the opposing traffic flag */
	void setOpposing(boolean ot);

	/** Get the opposing traffic flag */
	boolean getOpposing();

	/** Set prerequisite gate arm array */
	void setPrereq(String pr);

	/** Get prerequisite gate arm array */
	String getPrereq();

	/** Set verification camera */
	void setCamera(Camera c);

	/** Get verification camera */
	Camera getCamera();

	/** Set approach camera */
	void setApproach(Camera c);

	/** Get approach camera */
	Camera getApproach();

	/** Set the action plan */
	void setActionPlan(ActionPlan ap);

	/** Get the action plan */
	ActionPlan getActionPlan();

	/** Set the next state owner */
	void setOwnerNext(User o);

	/** Set the next arm state (request change) */
	void setArmStateNext(int gas);

	/** Get the (aggregate) arm state */
	int getArmState();

	/** Get the interlock ordinal */
	int getInterlock();
}
