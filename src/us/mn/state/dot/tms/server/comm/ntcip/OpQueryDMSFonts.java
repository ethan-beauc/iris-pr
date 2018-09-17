/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2018  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server.comm.ntcip;

import java.io.IOException;
import us.mn.state.dot.tms.server.DMSImpl;
import us.mn.state.dot.tms.server.comm.CommMessage;
import us.mn.state.dot.tms.server.comm.PriorityLevel;
import us.mn.state.dot.tms.server.comm.ntcip.mib1203.*;
import static us.mn.state.dot.tms.server.comm.ntcip.mib1203.MIB1203.*;
import us.mn.state.dot.tms.server.comm.snmp.ASN1Enum;
import us.mn.state.dot.tms.server.comm.snmp.ASN1Integer;
import us.mn.state.dot.tms.server.comm.snmp.ASN1OctetString;
import us.mn.state.dot.tms.server.comm.snmp.ASN1String;
import us.mn.state.dot.tms.server.comm.snmp.NoSuchName;

/**
 * Operation to query all fonts on a DMS controller.
 *
 * @author Douglas Lau
 */
public class OpQueryDMSFonts extends OpDMS {

	/** Make a font status object */
	static private ASN1Enum<FontStatus> makeStatus(int row) {
		return new ASN1Enum<FontStatus>(FontStatus.class,
			fontStatus.node, row);
	}

	/** Number of fonts supported */
	private final ASN1Integer num_fonts = numFonts.makeInt();

	/** Maximum number of characters in a font */
	private final ASN1Integer max_characters = maxFontCharacters.makeInt();

	/** Flag to indicate support for fontStatus object */
	private boolean version2;

	/** Create a new operation to query fonts from a DMS */
	public OpQueryDMSFonts(DMSImpl d) {
		super(PriorityLevel.DEVICE_DATA, d);
	}

	/** Create the second phase of the operation */
	@Override
	protected Phase phaseTwo() {
		return new Query1203Version();
	}

	/** Phase to determine if v2 or greater */
	private class Query1203Version extends Phase {

		/** Query the maximum character size (v2 only) */
		@SuppressWarnings("unchecked")
		protected Phase poll(CommMessage mess) throws IOException {
			ASN1Integer max_char = fontMaxCharacterSize.makeInt();
			mess.add(max_char);
			try {
				mess.queryProps();
				logQuery(max_char);
				version2 = true;
			}
			catch (NoSuchName e) {
				// Note: if this object doesn't exist, then the
				//       sign must not support v2.
				version2 = false;
			}
			return new QueryNumFonts();
		}
	}

	/** Phase to query the number of supported fonts */
	private class QueryNumFonts extends Phase {

		/** Query the number of supported fonts */
		@SuppressWarnings("unchecked")
		protected Phase poll(CommMessage mess) throws IOException {
			mess.add(num_fonts);
			mess.add(max_characters);
			mess.queryProps();
			logQuery(num_fonts);
			logQuery(max_characters);
			return new QueryFont(1);
		}
	}

	/** Phase to query one row of font table */
	private class QueryFont extends Phase {

		/** Row to query */
		private final int row;

		/** Create a query font phase */
		private QueryFont(int r) {
			row = r;
		}

		/** Query the font number for one row in font table */
		@SuppressWarnings("unchecked")
		protected Phase poll(CommMessage mess) throws IOException {
			ASN1Integer number = fontNumber.makeInt(row);
			ASN1String name = new ASN1String(fontName.node, row);
			ASN1Integer height = fontHeight.makeInt(row);
			ASN1Integer char_spacing = fontCharSpacing.makeInt(row);
			ASN1Integer line_spacing = fontLineSpacing.makeInt(row);
			ASN1Integer version = fontVersionID.makeInt(row);
			ASN1Enum<FontStatus> status = makeStatus(row);
			mess.add(number);
			mess.add(name);
			mess.add(height);
			mess.add(char_spacing);
			mess.add(line_spacing);
			mess.add(version);
			if (version2)
				mess.add(status);
			try {
				mess.queryProps();
				logQuery(number);
				logQuery(name);
				logQuery(height);
				logQuery(char_spacing);
				logQuery(line_spacing);
				logQuery(version);
				if (version2)
					logQuery(status);
			}
			catch (NoSuchName e) {
				// Note: some vendors respond with NoSuchName
				//       if the font is not valid
				return nextFont(row);
			}
			return new QueryCharacter(row, 1);
		}
	}

	/** Get phase to query the next font */
	private Phase nextFont(int r) {
		if (r < num_fonts.getInteger())
			return new QueryFont(r + 1);
		else
			return null;
	}

	/** Phase to query one character */
	private class QueryCharacter extends Phase {

		/** Font row */
		private final int row;

		/** Character row */
		private final int crow;

		/** Create a new add character phase */
		public QueryCharacter(int r, int cr) {
			row = r;
			crow = cr;
		}

		/** Add a character to the font table */
		@SuppressWarnings("unchecked")
		protected Phase poll(CommMessage mess) throws IOException {
			ASN1Integer char_width = characterWidth.makeInt(row,
				crow);
			ASN1OctetString char_bitmap = new ASN1OctetString(
				characterBitmap.node, row, crow);
			mess.add(char_width);
			mess.add(char_bitmap);
			try {
				mess.queryProps();
			}
			catch (NoSuchName e) {
				return nextFont(row);
			}
			logQuery(char_width);
			logQuery(char_bitmap);
			return nextCharacter(row, crow);
		}
	}

	/** Get phase to query the next character in a font */
	private Phase nextCharacter(int r, int cr) {
		if (cr < max_characters.getInteger())
			return new QueryCharacter(r, cr + 1);
		else
			return nextFont(r);
	}
}
