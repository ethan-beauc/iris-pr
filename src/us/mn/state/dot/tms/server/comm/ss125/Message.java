/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2009  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server.comm.ss125;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import us.mn.state.dot.tms.server.ControllerImpl;
import us.mn.state.dot.tms.server.comm.AddressedMessage;
import us.mn.state.dot.tms.server.comm.ChecksumException;
import us.mn.state.dot.tms.server.comm.ControllerException;
import us.mn.state.dot.tms.server.comm.ParsingException;

/**
 * SS125 message
 *
 * @author Douglas Lau
 */
public class Message implements AddressedMessage {

	/** Polynomial for CRC */
	static protected final int POLYNOMIAL = 0x1c;

	/** Look-up table for CRC calculations */
	static protected final byte[] CRC_TABLE = new byte[256];

	/** Initialize the lookup table */
	static {
		for(int i = 0; i < CRC_TABLE.length; i++) {
			int v = i;
		        for(int j = 0; j < 8; j++) {
				if((v & 0x80) != 0)
					v = (v << 1) ^ POLYNOMIAL;
				else
					v = v << 1;
			}
			CRC_TABLE[i] = (byte)v;
		}
	}

	/** Calculate the CRC-8 of a buffer.
	 * @param buffer Buffer to be checked.
	 * @return CRC-8 of the buffer. */
	static protected byte crc8(byte[] buffer) {
		int crc = 0;
		for(byte b: buffer)
			crc = CRC_TABLE[(crc ^ b) & 0xFF];
		return (byte)crc;
	}

	/** Sub ID must be configured to zero */
	static protected final byte SUB_ID = (byte)0;

	/** Maximum number of octets in message body */
	static protected final int MAX_BODY_OCTETS = 244;

	/** Serial output stream */
	protected final OutputStream output;

	/** Serial input stream */
	protected final InputStream input;

	/** SS125 destination ID (drop address) */
	protected final short dest_id;

	/** Request object */
	protected Request req;

	/** Create a new SS125 message.
	 * @param out Output stream to write message data.
	 * @param is Input stream to read message responses.
	 * @param c Controller to send messages. */
	public Message(OutputStream out, InputStream is, ControllerImpl c) {
		output = out;
		input = is;
		dest_id = c.getDrop();
	}

	/** Add a request object to this message */
	public void add(Object mo) {
		assert req == null;
		assert mo instanceof Request;
		req = (Request)mo;
	}

	/** Perform a GET request.
	 * @throws IOException On any errors sending message or receiving
	 *         response */
	public void getRequest() throws IOException {
		assert req != null;
		byte[] body = req.formatBodyGet();
		byte[] header = formatHeader(body);
		doPoll(header, body);
		req.parsePayload(doResponse(header, body));
	}

	/** Perform a SET request.
	 * @throws IOException On any errors sending message or receiving
	 *         response */
	public void setRequest() throws IOException {
		assert req != null;
		byte[] body = req.formatBodySet();
		byte[] header = formatHeader(body);
		doPoll(header, body);
		parseResult(doResponse(header, body));
	}

	/** Format a request header.
	 * @param body Body of message to send.
	 * @return Header appropriate for polling message. */
	protected byte[] formatHeader(byte[] body) {
		assert body.length <= MAX_BODY_OCTETS;
		byte[] header = new byte[10];
		header[0] = 'Z';			// Sentinel
		header[1] = '1';			// Protozol version
		header[2] = SUB_ID;			// Dest Sub ID
		header[3] = (byte)((dest_id >> 8) & 0xFF); // Dest ID (hi)
		header[4] = (byte)(dest_id & 0xFF);	// Dest ID (low)
		header[5] = (byte)0;			// Source Sub ID
		header[6] = (byte)0;			// Source ID (hi)
		header[7] = (byte)0;			// Source ID (low)
		header[8] = (byte)0;			// Sequence # FIXME?
		header[9] = (byte)body.length;		// Body length
		return header;
	}

	/** Perform a message poll.
	 * @param header Header of message.
	 * @param body Body of message.
	 * @throws IOException On any errors sending message. */
	protected void doPoll(byte[] header, byte[] body) throws IOException {
		input.skip(input.available());
		BufferedOutputStream bos = new BufferedOutputStream(output,256);
		bos.write(header);
		bos.write(crc8(header));
		bos.write(body);
		bos.write(crc8(body));
		bos.flush();
	}

	/** Receive a message response.
	 * @param shead Header of message sent.
	 * @param sbody Body of message sent.
	 * @return Body of message received.
	 * @throws IOException On any errors receiving response. */
	protected byte[] doResponse(byte[] shead, byte[] sbody)
		throws IOException
	{
		byte[] rhead = recvResponse(10);
		byte h_crc = recvResponse(1)[0];
		int n_body = parseHead(rhead, h_crc, shead);
		byte[] rbody = recvResponse(n_body);
		byte b_crc = recvResponse(1)[0];
		parseBody(rbody, b_crc, sbody);
		return rbody;
	}

	/** Receive part of a response.
	 * @param n_bytes Number of bytes to receive.
	 * @return Response received.
	 * @throws IOException On any errors receiving response. */
	protected byte[] recvResponse(int n_bytes) throws IOException {
		byte[] resp = new byte[n_bytes];
		int n_rcv = 0;
		while(n_rcv < n_bytes) {
			int r = input.read(resp, n_rcv, n_bytes - n_rcv);
			if(r <= 0)
				throw new EOFException("END OF STREAM");
			n_rcv += r;
		}
		return resp;
	}

	/** Parse a message response header.
	 * @param rhead Received response header.
	 * @param crc Received header crc.
	 * @param shead Sent message header.
	 * @return Number of bytes in response body.
	 * @throws ParsingException On any errors parsing response header. */
	protected int parseHead(byte[] rhead, byte crc, byte[] shead)
		throws ParsingException
	{
		assert rhead.length == 10;
		assert shead.length == 10;
		if(crc != crc8(rhead))
			throw new ChecksumException("HEADER");
		if(rhead[0] != 'Z')
			throw new ParsingException("SENTINEL");
		if(rhead[1] != '1')
			throw new ParsingException("VERSION");
		if(rhead[2] != shead[5])
			throw new ParsingException("DEST SUB ID");
		if(rhead[3] != shead[6])
			throw new ParsingException("DEST ID");
		if(rhead[4] != shead[7])
			throw new ParsingException("DEST ID");
		if(rhead[5] != shead[2])
			throw new ParsingException("SRC SUB ID");
		if(rhead[6] != shead[3])
			throw new ParsingException("SRC ID");
		if(rhead[7] != shead[4])
			throw new ParsingException("SRC ID");
		if(rhead[8] != shead[8] + 1)
			throw new ParsingException("SEQUENCE");
		int n_body = rhead[9] & 0xFF;
		if(n_body < 3 || n_body > MAX_BODY_OCTETS)
			throw new ParsingException("BODY SIZE");
		return n_body;
	}

	/** Parse a message response body.
	 * @param rbody Received response body.
	 * @param crc Received body crc.
	 * @param sbody Send message body.
	 * @throws ParsingException On any errors parsing response body. */
	protected void parseBody(byte[] rbody, byte crc, byte[] sbody)
		throws ParsingException
	{
		assert rbody.length >= 3;
		assert sbody.length >= 3;
		if(crc != crc8(rbody))
			throw new ChecksumException("BODY");
		if(rbody[0] != sbody[0])
			throw new ParsingException("MESSAGE ID");
		if(rbody[1] != sbody[1])
			throw new ParsingException("MESSAGE SUB ID");
		if(rbody[2] != sbody[2])
			throw new ParsingException("READ OR WRITE");
	}

	/** Parse the result code.
	 * @param rbody Received response body.
	 * @throws ParsingException On any errors parsing result code.
	 * @throws ControllerException If result indicates an error. */
	protected void parseResult(byte[] rbody) throws IOException {
		if(rbody.length != 5)
			throw new ParsingException("RESULT LENGTH");
		int result = Request.parse16(rbody, 3);
		if(result > 0)
			throw new ControllerException(lookupResult(result));
	}

	/** Lookup a result code */
	static protected String lookupResult(int result) {
		switch(result) {
		case 1:
			return "PAYLOAD SIZE";
		case 2:
			return "BODY CRC";
		case 3:
			return "READ ONLY";
		case 15:
			return "INTERVAL NONEXISTANT";
		case 16:
			return "LANE NONEXISTANT";
		case 17:
			return "FLASH BUSY (A)";
		case 19:
			return "INVALID PUSH STATE";
		case 20:
			return "ERROR SETTING RTC";
		case 21:
			return "RTC SYNC ERROR";
		case 22:
			return "FLASH ERASE ERROR";
		case 23:
			return "FLASH BUSY (B)";
		case 24:
			return "INVALID PROTOCOL STATE";
		case 25:
			return "TOO MANY APPROACHES";
		case 26:
			return "TOO MANY LANES";
		case 30:
			return "AUTOMATIC LANE";
		case 31:
			return "WRONG LANE COUNT";
		case 33:
			return "INVALID BAUD RATE";
		default:
			return "UNKNOWN ERROR";
		}
	}
}
