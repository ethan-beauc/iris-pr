// Copyright (C) 2022-2024  Minnesota Department of Transportation
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
use crate::card::{inactive_attr, Card, View};
use crate::device::{Device, DeviceAnc};
use crate::util::{ContainsLower, Fields, HtmlStr, Input, OptVal};
use resources::Res;
use serde::{Deserialize, Serialize};
use std::borrow::Cow;

/// GPS
#[derive(Debug, Default, Deserialize, Serialize, PartialEq)]
pub struct Gps {
    pub name: String,
    pub controller: Option<String>,
    pub notes: Option<String>,
    // secondary attributes
    pub pin: Option<u32>,
}

type GpsAnc = DeviceAnc<Gps>;

impl Gps {
    /// Convert to Compact HTML
    fn to_html_compact(&self, anc: &GpsAnc) -> String {
        let name = HtmlStr::new(self.name());
        let inactive = inactive_attr(self.controller.is_some());
        let item_state = anc.item_state(self);
        format!("<div class='end{inactive}'>{name} {item_state}</div>")
    }

    /// Convert to Setup HTML
    fn to_html_setup(&self, anc: &GpsAnc) -> String {
        let title = self.title(View::Setup);
        let controller = anc.controller_html();
        let pin = OptVal(self.pin);
        format!(
            "{title}\
            {controller}\
            <div class='row'>\
              <label for='pin'>Pin</label>\
              <input id='pin' type='number' min='1' max='104' \
                     size='8' value='{pin}'>\
            </div>"
        )
    }
}

impl Device for Gps {
    /// Get controller
    fn controller(&self) -> Option<&str> {
        self.controller.as_deref()
    }
}

impl Card for Gps {
    type Ancillary = GpsAnc;

    /// Display name
    const DNAME: &'static str = "🌐 Gps";

    /// Get the resource
    fn res() -> Res {
        Res::Gps
    }

    /// Get the name
    fn name(&self) -> Cow<str> {
        Cow::Borrowed(&self.name)
    }

    /// Set the name
    fn with_name(mut self, name: &str) -> Self {
        self.name = name.to_string();
        self
    }

    /// Check if a search string matches
    fn is_match(&self, search: &str, _anc: &GpsAnc) -> bool {
        self.name.contains_lower(search) || self.notes.contains_lower(search)
    }

    /// Convert to HTML view
    fn to_html(&self, view: View, anc: &GpsAnc) -> String {
        match view {
            View::Create => self.to_html_create(anc),
            View::Setup => self.to_html_setup(anc),
            _ => self.to_html_compact(anc),
        }
    }

    /// Get changed fields from Setup form
    fn changed_fields(&self) -> String {
        let mut fields = Fields::new();
        fields.changed_input("controller", &self.controller);
        fields.changed_input("pin", self.pin);
        fields.into_value().to_string()
    }
}
