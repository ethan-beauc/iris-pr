/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2007-2009  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.client.roads;

import us.mn.state.dot.tms.GeoLoc;
import us.mn.state.dot.tms.GeoLocHelper;

/**
 * Simple 2D vector class
 *
 * @author Douglas Lau
 */
public class Vector {

	/** Get the vector to a location from the origin */
	static public Vector create(GeoLoc loc) {
		if(loc != null) {
			int x = GeoLocHelper.getTrueEasting(loc);
			int y = GeoLocHelper.getTrueNorthing(loc);
			return new Vector(x, y);
		} else
			return new Vector(0, 0);
	}

	/** X-coordinate */
	public final double x;

	/** Y-coordinate */
	public final double y;

	/** Create a new vector */
	public Vector(double x, double y) {
		this.x = x;
		this.y = y;
	}

	/** Get the magnitude (length) */
	public double getMagnitude() {
		return Math.hypot(x, y);
	}

	/** Get the vector angle (radians) */
	public double getAngle() {
		double a = Math.acos(x / getMagnitude());
		if(y > 0)
			return a;
		else
			return -a;
	}

	/** Add a vector to this one */
	public Vector add(Vector other) {
		return new Vector(x + other.x, y + other.y);
	}

	/** Subtract another vector from this one */
	public Vector subtract(Vector other) {
		return new Vector(x - other.x, y - other.y);
	}

	/** Calculate the dot product with another vector */
	public double dot(Vector other) {
		return x * other.x + y * other.y;
	}

	/** Calculate the cross product with another vector.  This returns
	 * the magnitude of the 3D cross product, since cross product only
	 * makes sense for 3D vectors. */
	public double cross(Vector other) {
		return x * other.y - y * other.x;
	}

	/** Get a perpendicular vector */
	public Vector perpendicular() {
		return new Vector(y, -x);
	}
}
