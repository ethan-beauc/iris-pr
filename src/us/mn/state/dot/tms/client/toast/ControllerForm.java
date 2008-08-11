/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2008  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.client.toast;

import java.awt.Color;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import us.mn.state.dot.sched.ActionJob;
import us.mn.state.dot.sched.ChangeJob;
import us.mn.state.dot.sched.FocusJob;
import us.mn.state.dot.sonar.client.TypeCache;
import us.mn.state.dot.tms.CommLink;
import us.mn.state.dot.tms.Controller;
import us.mn.state.dot.tms.client.SonarState;
import us.mn.state.dot.tms.client.TmsConnection;
import us.mn.state.dot.tms.client.proxy.ProxyListModel;

/**
 * ControllerForm is a Swing dialog for editing Controller records
 *
 * @author Douglas Lau
 */
public class ControllerForm extends SonarObjectForm<Controller> {

	/** Frame title */
	static protected final String TITLE = "Controller: ";

	/** Comm link combo box */
	protected final JComboBox comm_link = new JComboBox();

	/** Model for drop address spinner */
	protected DropNumberModel drop_model;

	/** Drop address spinner */
	protected final JSpinner drop_id = new JSpinner();

	/** Controller notes text */
	protected final JTextArea notes = new JTextArea();

	/** Active checkbox */
	protected final JCheckBox active = new JCheckBox();

	/** Location panel */
	protected LocationPanel location;

	/** Mile point text field */
	protected final JTextField mile = new JTextField(10);

	/** Cabinet style combo box */
	protected final JComboBox cab_style = new JComboBox();

	/** Reset button */
	protected final JButton reset = new JButton("Reset");

	/** Comm Link list model */
	protected final ProxyListModel<CommLink> link_model;

	/** Create a new controller form */
	public ControllerForm(TmsConnection tc, Controller c) {
		super(TITLE, tc, c);
		TypeCache<CommLink> links = tc.getSonarState().getCommLinks();
		link_model = new ProxyListModel<CommLink>(links);
	}

	/** Get the SONAR type cache */
	protected TypeCache<Controller> getTypeCache(SonarState st) {
		return st.getControllers();
	}

	/** Initialize the widgets on the form */
	protected void initialize() {
		super.initialize();
		link_model.initialize();
		comm_link.setModel(new WrapperComboBoxModel(link_model, false));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		JTabbedPane tab = new JTabbedPane();
		tab.add("Setup", createSetupPanel());
		tab.add("Cabinet", createCabinetPanel());
		tab.add("I/O", createIOPanel());
		add(tab);
		updateAttribute(null);
		setBackground(Color.LIGHT_GRAY);
	}

	/** Dispose of the form */
	protected void dispose() {
		link_model.dispose();
		super.dispose();
	}

	/** Create the controller setup panel */
	protected JPanel createSetupPanel() {
		FormPanel panel = new FormPanel(admin);
		panel.addRow("Comm Link", comm_link);
		panel.addRow("Drop", drop_id);
		new ChangeJob(this, drop_id) {
			public void perform() {
				Number n = (Number)drop_id.getValue();
				short d = n.shortValue();
				// Avoid ping-pong looping...
				if(d != proxy.getDrop())
					proxy.setDrop(d);
			}
		};
		panel.addRow("Notes", notes);
		new FocusJob(notes) {
			public void perform() {
				if(wasLost())
					proxy.setNotes(notes.getText());
			}
		};
		panel.add("Active", active);
		// Add a third column to the grid bag so the drop spinner
		// does not extend across the whole form
		panel.addRow(new javax.swing.JLabel());
		active.setEnabled(connection.isAdmin() ||
			connection.isActivate());
		new ActionJob(this, active) {
			public void perform() {
				proxy.setActive(active.isSelected());
			}
		};
		return panel;
	}

	/** Create the cabinet panel */
	protected JPanel createCabinetPanel() {
		location = new LocationPanel(admin,
			proxy.getCabinet().getGeoLoc(),
			connection.getSonarState());
		location.initialize();
		location.addRow("Milepoint", mile);
		location.addRow("Style", cab_style);
		return location;
	}

	/** Create the I/O panel */
	protected JPanel createIOPanel() {
		FormPanel panel = new FormPanel(admin);
		return panel;
	}

	/** Update one attribute on the form */
	protected void updateAttribute(String a) {
		if(a == null || a.equals("comm_link")) {
			if(comm_link.getSelectedItem() != proxy.getCommLink()) {
				comm_link.setSelectedItem(proxy.getCommLink());
				drop_model = new DropNumberModel(
					proxy.getCommLink(), getTypeCache(
					connection.getSonarState()));
				drop_id.setModel(drop_model);
			}
		}
		if(a == null || a.equals("drop"))
			drop_id.setValue(proxy.getDrop());
		if(a == null || a.equals("notes"))
			notes.setText(proxy.getNotes());
		if(a == null || a.equals("active"))
			active.setSelected(proxy.getActive());
		// FIXME: update the other attributes
	}
}
