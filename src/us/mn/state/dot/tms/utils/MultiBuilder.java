/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2006-2024  Minnesota Department of Transportation
 * Copyright (C) 2019-2020  SRF Consulting Group
 * Copyright (C) 2021  Iteris Inc.
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
package us.mn.state.dot.tms.utils;

/**
 * MULTI string builder (MarkUp Language for Transportation Information), as
 * specified in NTCIP 1203.
 *
 * @author Douglas Lau
 * @author John Stanley - SRF Consulting
 * @author Gordon Parikh - SRF Consulting
 * @author Michael Darter
 */
public class MultiBuilder implements Multi {

	/** MULTI string builder */
	private final StringBuilder multi = new StringBuilder();

	/** Get the string value */
	@Override
	public String toString() {
		return multi.toString();
	}

	/** Get the MULTI string */
	public MultiString toMultiString() {
		return new MultiString(toString());
	}

	/** Create an empty MULTI builder */
	public MultiBuilder() { }

	/** Create a new MULTI builder.
	 * @param ms MULTI string.
	 * @throws NullPointerException if ms is null. */
	public MultiBuilder(String ms) {
		if (ms != null)
			multi.append(ms);
		else
			throw new NullPointerException();
	}

	/** Append another MULTI string */
	public void append(MultiString ms) {
		multi.append(ms.toString());
	}

	/** Clear the MULTI builder */
	public void clear() {
		multi.setLength(0);
	}

	/** Append an Integer if it isn't null. */
	private void appendInteger(Integer x) {
		if (x != null)
			multi.append(x);
	}

	/** Handle an unsupported tag */
	@Override
	public void unsupportedTag(String tag) {
		// ignore unsupported tags
	}

	/** Add a span of text */
	@Override
	public void addSpan(String s) {
		multi.append(s.replace("[", "[[").replace("]", "]]"));
	}

	/** Add a new line */
	@Override
	public void addLine(Integer spacing) {
		multi.append("[nl");
		appendInteger(spacing);
		multi.append("]");
	}

	/** Add a new page */
	@Override
	public void addPage() {
		multi.append("[np]");
	}

	/** Set the page times.
	 * @param pt_on Page on time (deciseconds; null means default)
	 * @param pt_off Page off time (deciseconds; null means default) */
	@Override
	public void setPageTimes(Integer pt_on, Integer pt_off) {
		multi.append("[pt");
		appendInteger(pt_on);
		multi.append('o');
		appendInteger(pt_off);
		multi.append("]");
	}

	/** Set the page justification.
	 * Use the sign's default page justification if jp is null. */
	@Override
	public void setJustificationPage(JustificationPage jp) {
		multi.append("[jp");
		if (jp != null && jp != JustificationPage.UNDEFINED)
			multi.append(jp.ordinal());
		multi.append("]");
	}

	/** Set the line justification.
	 * Use the sign's default line justification if jl is null. */
	@Override
	public void setJustificationLine(JustificationLine jl) {
		multi.append("[jl");
		if (jl != null && jl != JustificationLine.UNDEFINED)
			multi.append(jl.ordinal());
		multi.append("]");
	}

	/** Add a graphic */
	@Override
	public void addGraphic(int g_num, Integer x, Integer y,
		String g_id)
	{
		multi.append("[g");
		multi.append(g_num);
		if (x != null && y != null) {
			multi.append(',');
			multi.append(x);
			multi.append(',');
			multi.append(y);
			if (g_id != null) {
				multi.append(',');
				multi.append(g_id);
			}
		}
		multi.append("]");
	}

	/** Set the font number.
	 * @param f_num Font number (1 to 255)
	 * @param f_id Font version ID (4-digit hex string)
	 * Use the sign's default font if f_num is null. */
	@Override
	public void setFont(Integer f_num, String f_id) {
		multi.append("[fo");
		appendInteger(f_num);
		if (f_num != null && f_id != null) {
			multi.append(',');
			multi.append(f_id);
		}
		multi.append("]");
	}

	/** Set the character spacing.
	 * @param sc Character spacing (null means use font spacing) */
	@Override
	public void setCharSpacing(Integer sc) {
		if (sc != null) {
			multi.append("[sc");
			multi.append(sc);
		} else
			multi.append("[/sc");
		multi.append("]");
	}

	/** Set the (deprecated) message background color.
	 * @param x Background color (0-9; colorClassic value).
	 * Use the sign's default background color if x is null. */
	@Override
	public void setColorBackground(Integer x) {
		multi.append("[cb");
		appendInteger(x);
		multi.append("]");
	}

	/** Set the page background color for monochrome1bit, monochrome8bit,
	 * and colorClassic color schemes.
	 * @param x Background color (0-1 for monochrome1bit),
	 *                           (0-255 for monochrome8bit),
	 *                           (0-9 for colorClassic).
	 * Use the sign's default background color if x is null. */
	@Override
	public void setPageBackground(Integer x) {
		multi.append("[pb");
		appendInteger(x);
		multi.append("]");
	}

	/** Set the page background color for color24bit color scheme.
	 * @param red Red component (0-255).
	 * @param green Green component (0-255).
	 * @param blue Blue component (0-255). */
	@Override
	public void setPageBackground(int red, int green, int blue) {
		multi.append("[pb");
		multi.append(red);
		multi.append(',');
		multi.append(green);
		multi.append(',');
		multi.append(blue);
		multi.append("]");
	}

	/** Set the foreground color for a single-int color tag.  [cfX]
	 * @param x Foreground color (0-1 for monochrome1bit),
	 *                           (0-255 for monochrome8bit),
	 *                           (0-9 for colorClassic &amp; color24bit).
	 * Use the sign's default foreground color if x is null. */
	@Override
	public void setColorForeground(Integer x) {
		multi.append("[cf");
		appendInteger(x);
		multi.append("]");
	}

	/** Set the foreground color for color24bit color scheme.
	 * @param red Red component (0-255).
	 * @param green Green component (0-255).
	 * @param blue Blue component (0-255). */
	@Override
	public void setColorForeground(int red, int green, int blue) {
		multi.append("[cf");
		multi.append(red);
		multi.append(',');
		multi.append(green);
		multi.append(',');
		multi.append(blue);
		multi.append("]");
	}

	/** Add a color rectangle for monochrome1bit, monochrome8bit, and
	 * colorClassic color schemes.
	 * @param x X pixel position of upper left corner.
	 * @param y Y pixel position of upper left corner.
	 * @param w Width in pixels.
	 * @param h Height in pixels.
	 * @param z Color of rectangle (0-1 for monochrome1bit),
	 *                             (0-255 for monochrome8bit),
	 *                             (0-9 for colorClassic). */
	@Override
	public void addColorRectangle(int x, int y, int w, int h,
		int z)
	{
		multi.append("[cr");
		multi.append(x);
		multi.append(',');
		multi.append(y);
		multi.append(',');
		multi.append(w);
		multi.append(',');
		multi.append(h);
		multi.append(',');
		multi.append(z);
		multi.append("]");
	}

	/** Add a color rectangle for color24bit color scheme.
	 * @param x X pixel position of upper left corner.
	 * @param y Y pixel position of upper left corner.
	 * @param w Width in pixels.
	 * @param h Height in pixels.
	 * @param r Red component (0-255).
	 * @param g Green component (0-255).
	 * @param b Blue component (0-255). */
	@Override
	public void addColorRectangle(int x, int y, int w, int h,
		int r, int g, int b)
	{
		multi.append("[cr");
		multi.append(x);
		multi.append(',');
		multi.append(y);
		multi.append(',');
		multi.append(w);
		multi.append(',');
		multi.append(h);
		multi.append(',');
		multi.append(r);
		multi.append(',');
		multi.append(g);
		multi.append(',');
		multi.append(b);
		multi.append("]");
	}

	/** Set the text rectangle */
	@Override
	public void setTextRectangle(int x, int y, int w, int h) {
		multi.append("[tr");
		multi.append(x);
		multi.append(',');
		multi.append(y);
		multi.append(',');
		multi.append(w);
		multi.append(',');
		multi.append(h);
		multi.append("]");
	}

	/** Add a travel time destination.
	 * @param sid Destination station ID.
	 * @param mode Over limit mode.
	 * @param o_txt Over limit text. */
	@Override
	public void addTravelTime(String sid, OverLimitMode mode, String o_txt){
		multi.append("[tt");
		multi.append(sid);
		if (mode != null) {
			multi.append(',');
			multi.append(mode);
			if (o_txt != null) {
				multi.append(',');
				multi.append(o_txt);
			}
		}
		multi.append("]");
	}

	/** Add a speed advisory */
	@Override
	public void addSpeedAdvisory() {
		multi.append("[vsa]");
	}

	/** Add a ClearGuide advisory
	 * @param dms DMS name
	 * @param wid Workzone id
	 * @param tsp Threshold speed
	 * @param mode Variable to use: tt, delay
	 * @param ridx Route index, zero based */
	@Override
	public void addClearGuideAdvisory(
		String dms, int wid, int tsp, String mode, int ridx)
	{
		multi.append("[cg");
		multi.append(dms).append(",");
		multi.append(wid).append(",");
		multi.append(tsp).append(",");
		multi.append(mode).append(",");
		multi.append(ridx);
		multi.append("]");
	}

	/** Add a slow traffic warning.
	 * @param spd Highest speed to activate warning.
	 * @param dist Distance to search for slow traffic (1/10 mile).
	 * @param mode Tag replacement mode (none, dist or speed). */
	@Override
	public void addSlowWarning(int spd, int dist, String mode) {
		multi.append("[slow");
		multi.append(spd);
		multi.append(',');
		multi.append(dist);
		if ("dist".equals(mode) || "speed".equals(mode)) {
			multi.append(',');
			multi.append(mode);
		}
		multi.append("]");
	}

	/** Add an exit backup warning.
	 * @param did Exit detector ID.
	 * @param occ Occupancy threshold to activate warning. */
	@Override
	public void addExitWarning(String did, int occ) {
		multi.append("[exit");
		multi.append(did);
		multi.append(',');
		multi.append(occ);
		multi.append("]");
	}

	/** Add a feed message */
	@Override
	public void addFeed(String fid) {
		multi.append("[feed");
		multi.append(fid);
		multi.append("]");
	}

	/** Add a tolling message */
	@Override
	public void addTolling(String mode, String[] zones) {
		multi.append("[tz");
		multi.append(mode);
		for (String z: zones) {
			multi.append(',');
			multi.append(z);
		}
		multi.append("]");
	}

	/** Add parking area availability.
	 * @param pid Parking area ID.
	 * @param l_txt Text for low availability.
	 * @param c_txt Text for closed area. */
	@Override
	public void addParking(String pid, String l_txt, String c_txt) {
		if (pid.startsWith("pa")) {
			multi.append("[");
			multi.append(pid);
			if (l_txt != null) {
				multi.append(',');
				multi.append(l_txt);
				if (c_txt != null) {
					multi.append(',');
					multi.append(c_txt);
				}
			}
			multi.append("]");
		}
	}

	/** Add RWIS condition warning.
	 * @param condition Weather condition.
	 * @param level Warning level. */
	@Override
	public void addRwis(String condition, int level) {
		multi.append("[rwis_");
		multi.append(condition);
		multi.append(',');
		multi.append(level);
		multi.append("]");
	}

	/** Add a time action substitution field.
	 *  @param dir Chronoligical direction ("n" or "p").
	 *  @param format Format pattern (DateTimeFormattter). */
	@Override
	public void addTimeAction(String dir, String format) {
		multi.append("[ta");
		if ("p".equalsIgnoreCase(dir))
			multi.append("p");
		else
			multi.append("n");
		if (format != null) {
			multi.append(',');
			multi.append(format);
		}
		multi.append("]");
	}

	/** Add an incident locator */
	@Override
	public void addLocator(String code) {
		multi.append("[loc");
		multi.append(code);
		multi.append("]");
	}

	/** Add a standby-message tag */
	@Override
	public void addStandby() {
		multi.append("[standby]");
	}
}
