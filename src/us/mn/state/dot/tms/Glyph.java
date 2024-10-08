/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2007-2024  Minnesota Department of Transportation
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
 * A glyph defines the bitmap used for a single code point in a font.
 *
 * @author Douglas Lau
 */
public interface Glyph extends SonarObject {

	/** SONAR type name */
	String SONAR_TYPE = "glyph";

	/** Get the SONAR type name */
	@Override
	default String getTypeName() {
		return SONAR_TYPE;
	}

	/** SONAR base type name */
	String SONAR_BASE = DMS.SONAR_TYPE;

	/** Get the font */
	Font getFont();

	/** Get the code point */
	int getCodePoint();

	/** Set the width (pixels) */
	void setWidth(int w);

	/** Get the width (pixels) */
	int getWidth();

	/** Set the pixel data (base64 encoded). */
	void setPixels(String p);

	/** Get the pixel data (base64 encoded). */
	String getPixels();
}
