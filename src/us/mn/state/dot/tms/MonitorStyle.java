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
 * Video monitor style.
 *
 * @author Douglas Lau
 */
public interface MonitorStyle extends SonarObject {

	/** SONAR type name */
	String SONAR_TYPE = "monitor_style";

	/** Get the SONAR type name */
	@Override
	default String getTypeName() {
		return SONAR_TYPE;
	}

	/** SONAR base type name */
	String SONAR_BASE = VideoMonitor.SONAR_TYPE;

	/** Default accent color */
	String DEFAULT_ACCENT = "608060";

	/** Default font size (pt) */
	int DEFAULT_FONT_SZ = 32;

	/** Set force-aspect ratio flag */
	void setForceAspect(boolean fa);

	/** Get force-aspect ratio flag */
	boolean getForceAspect();

	/** Set the accent color (hex: RRGGBB) */
	void setAccent(String a);

	/** Get the accent color (hex: RRGGBB) */
	String getAccent();

	/** Set the font size (pt) */
	void setFontSz(int fs);

	/** Get the font size (pt) */
	int getFontSz();

	/** Set title-bar flag */
	void setTitleBar(boolean tb);

	/** Get title-bar flag */
	boolean getTitleBar();

	/** Set auto-expand flag */
	void setAutoExpand(boolean ae);

	/** Get auto-expand flag */
	boolean getAutoExpand();

	/** Set the horizontal gap */
	void setHGap(int g);

	/** Get the horizontal gap */
	int getHGap();

	/** Set the vertical gap */
	void setVGap(int g);

	/** Get the vertical gap */
	int getVGap();
}
