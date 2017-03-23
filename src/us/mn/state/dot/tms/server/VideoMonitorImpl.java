/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2007-2017  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.tms.Camera;
import us.mn.state.dot.tms.Controller;
import us.mn.state.dot.tms.ControllerHelper;
import us.mn.state.dot.tms.DeviceRequest;
import us.mn.state.dot.tms.TMSException;
import us.mn.state.dot.tms.VideoMonitor;
import us.mn.state.dot.tms.VideoMonitorHelper;
import us.mn.state.dot.tms.server.comm.DevicePoller;
import us.mn.state.dot.tms.server.comm.VideoMonitorPoller;
import us.mn.state.dot.tms.server.event.CameraSwitchEvent;

/**
 * A video monitor device.
 *
 * @author Douglas Lau
 */
public class VideoMonitorImpl extends DeviceImpl implements VideoMonitor {

	/** Check if the camera video should be published */
	static private boolean isCameraPublished(Camera c) {
		return c != null && c.getPublish();
	}

	/** Cast a camera to an impl or null */
	static private CameraImpl toCameraImpl(Camera c) {
		return (c instanceof CameraImpl) ? (CameraImpl) c : null;
	}

	/** Set camera on all video monitors with a given number */
	static public void setCameraNotify(int mn, CameraImpl c, String src) {
		setCameraNotify(null, mn, c, src);
	}

	/** Set camera on all video monitors with a given number.
	 * @param svm Video monitor to skip.
	 * @param mn Monitor number.
	 * @param c Camera to display.
	 * @param src Source of command. */
	static private void setCameraNotify(VideoMonitorImpl svm, int mn,
		CameraImpl c, String src)
	{
		Iterator<VideoMonitor> it = VideoMonitorHelper.iterator();
		while (it.hasNext()) {
			VideoMonitor m = it.next();
			if (svm != m && (m instanceof VideoMonitorImpl)) {
				VideoMonitorImpl vm = (VideoMonitorImpl) m;
				if (vm.getMonNum() == mn)
					vm.setCameraNotify(c, src);
			}
		}
	}

	/** Blank restricted video monitors viewing a camera */
	static public void blankRestrictedMonitors() {
		Iterator<VideoMonitor> it = VideoMonitorHelper.iterator();
		while (it.hasNext()) {
			VideoMonitor m = it.next();
			if (m instanceof VideoMonitorImpl) {
				VideoMonitorImpl vm = (VideoMonitorImpl) m;
				vm.blankRestricted();
			}
		}
	}

	/** Load all the video monitors */
	static protected void loadAll() throws TMSException {
		namespace.registerType(SONAR_TYPE, VideoMonitorImpl.class);
		store.query("SELECT name, controller, pin, notes, mon_num, " +
		            "direct, restricted, camera FROM iris." +
		            SONAR_TYPE + ";", new ResultFactory()
		{
			public void create(ResultSet row) throws Exception {
				namespace.addObject(new VideoMonitorImpl(row));
			}
		});
	}

	/** Get a mapping of the columns */
	@Override
	public Map<String, Object> getColumns() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("name", name);
		map.put("controller", controller);
		map.put("pin", pin);
		map.put("notes", notes);
		map.put("mon_num", mon_num);
		map.put("direct", direct);
		map.put("restricted", restricted);
		map.put("camera", camera);
		return map;
	}

	/** Get the database table name */
	@Override
	public String getTable() {
		return "iris." + SONAR_TYPE;
	}

	/** Get the SONAR type name */
	@Override
	public String getTypeName() {
		return SONAR_TYPE;
	}

	/** Create a video monitor */
	private VideoMonitorImpl(ResultSet row) throws SQLException {
		this(row.getString(1),		// name
		     row.getString(2),		// controller
		     row.getInt(3),		// pin
		     row.getString(4),		// notes
		     row.getInt(5),		// mon_num
		     row.getBoolean(6),		// direct
		     row.getBoolean(7),		// restricted
		     row.getString(8)		// camera
		);
	}

	/** Create a video monitor */
	private VideoMonitorImpl(String n, String c, int p, String nt, int mn,
		boolean d, boolean r, String cam)
	{
		this(n, lookupController(c), p, nt, mn, d, r,lookupCamera(cam));
	}

	/** Create a video monitor */
	private VideoMonitorImpl(String n, ControllerImpl c, int p, String nt,
		int mn, boolean d, boolean r, Camera cam)
	{
		super(n, c, p, nt);
		mon_num = mn;
		direct = d;
		restricted = r;
		camera = cam;
		initTransients();
	}

	/** Create a new video monitor */
	public VideoMonitorImpl(String n) throws TMSException, SonarException {
		super(n);
	}

	/** Monitor number */
	private int mon_num;

	/** Set the monitor number */
	@Override
	public void setMonNum(int mn) {
		mon_num = mn;
	}

	/** Set the monitor number */
	public void doSetMonNum(int mn) throws TMSException {
		if (mn != mon_num) {
			store.update(this, "mon_num", mn);
			setMonNum(mn);
		}
	}

	/** Get the monitor number */
	@Override
	public int getMonNum() {
		return mon_num;
	}

	/** Flag to connect direct to camera */
	private boolean direct;

	/** Set flag to connect direct to camera */
	@Override
	public void setDirect(boolean d) {
		direct = d;
	}

	/** Set flag to connect direct to camera */
	public void doSetDirect(boolean d) throws TMSException {
		if (d != direct) {
			store.update(this, "direct", d);
			setDirect(d);
		}
	}

	/** Get flag to connect directo to camera */
	@Override
	public boolean getDirect() {
		return direct;
	}

	/** Flag to restrict publishing camera images */
	private boolean restricted;

	/** Set flag to restrict publishing camera images */
	@Override
	public void setRestricted(boolean r) {
		restricted = r;
	}

	/** Set flag to restrict publishing camera images */
	public void doSetRestricted(boolean r) throws TMSException {
		if (r == restricted)
			return;
		store.update(this, "restricted", r);
		setRestricted(r);
		blankRestricted();
	}

	/** Blank restricted monitor */
	private void blankRestricted() {
		if (getRestricted() && !isCameraPublished(getCamera()))
			setCameraNotify(null, "RESTRICTED");
	}

	/** Get flag to restrict publishing camera images */
	@Override
	public boolean getRestricted() {
		return restricted;
	}

	/** Camera displayed on the video monitor */
	private Camera camera;

	/** Set the camera displayed on the monitor */
	@Override
	public void setCamera(Camera c) {
		camera = c;
	}

	/** Set the camera displayed on the monitor */
	public void doSetCamera(Camera c) throws TMSException {
		CameraImpl cam = toCameraImpl(c);
		String u = getProcUser();
		if (doSetCam(cam, u)) {
			// Switch all other monitors with same mon_num
			setCameraNotify(this, mon_num, cam, u);
		}
	}

	/** Set the camera displayed on the monitor.
	 * @param c Camera to display.
	 * @param src Source of request.
	 * @return true if switch was permitted. */
	private boolean doSetCam(CameraImpl c, String src)
		throws TMSException
	{
		boolean r = restricted && !isCameraPublished(c);
		if (r)
			c = null;
		if (c != camera) {
			store.update(this, "camera", c);
			setCamera(c);
			selectCamera(c, src);
		}
		return !r;
	}

	/** Set the camera and notify clients of the change */
	private void setCameraNotify(CameraImpl c, String src) {
		try {
			doSetCam(c, src);
			notifyAttribute("camera");
		}
		catch (TMSException e) {
			e.printStackTrace();
		}
	}

	/** Get the camera displayed on the monitor */
	@Override
	public Camera getCamera() {
		return camera;
	}

	/** Get the video monitor poller */
	private VideoMonitorPoller getVideoMonitorPoller() {
		DevicePoller dp = getPoller();
		return (dp instanceof VideoMonitorPoller)
		      ? (VideoMonitorPoller) dp
		      : null;
	}

	/** Send a device request operation */
	@Override
	protected void sendDeviceRequest(DeviceRequest dr) {
		VideoMonitorPoller vmp = getVideoMonitorPoller();
		if (vmp != null)
			vmp.sendRequest(this, dr);
	}

	/** Select a camera for the video monitor */
	private void selectCamera(CameraImpl cam, String src) {
		// NOTE: we need to iterate through all controllers to support
		//       Pelco switcher protocol.  Otherwise, we could just
		//       call getController here.
		selectCameraWithSwitcher(cam);
		String cid = (cam != null) ? cam.getName() : "";
		logEvent(new CameraSwitchEvent(getName(), cid, src));
	}

	/** Select a camera for the video monitor with a switcher */
	private void selectCameraWithSwitcher(CameraImpl cam) {
		Iterator<Controller> it = ControllerHelper.iterator();
		while (it.hasNext()) {
			Controller c = it.next();
			if (c instanceof ControllerImpl)
				selectCamera((ControllerImpl) c, cam);
		}
	}

	/** Select a camera for the video monitor */
	private void selectCamera(ControllerImpl c, CameraImpl cam) {
		DevicePoller dp = c.getPoller();
		if (dp instanceof VideoMonitorPoller) {
			VideoMonitorPoller vmp = (VideoMonitorPoller) dp;
			vmp.switchCamera(c, this, cam);
		}
	}
}
