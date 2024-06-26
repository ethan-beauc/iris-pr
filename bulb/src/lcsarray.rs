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
use crate::card::{AncillaryData, Card, View};
use crate::error::Result;
use crate::fetch::Uri;
use crate::util::{ContainsLower, Fields, HtmlStr};
use resources::Res;
use serde::{Deserialize, Serialize};
use std::borrow::Cow;
use std::iter::once;
use wasm_bindgen::JsValue;

/// LCS locks
#[derive(Debug, Deserialize, Serialize)]
pub struct LcsLock {
    pub id: u32,
    pub description: String,
}

/// LCS Array
#[derive(Debug, Default, Deserialize, Serialize, PartialEq)]
pub struct LcsArray {
    pub name: String,
    pub notes: Option<String>,
    pub lcs_lock: Option<u32>,
    // secondary attributes
    pub shift: Option<u32>,
}

/// Ancillary LCS array data
#[derive(Debug, Default)]
pub struct LcsArrayAnc {
    pub locks: Option<Vec<LcsLock>>,
}

impl LcsArrayAnc {
    /// Get lock description
    fn lock(&self, pri: &LcsArray) -> &str {
        if let (Some(lcs_lock), Some(locks)) = (pri.lcs_lock, &self.locks) {
            for lock in locks {
                if lcs_lock == lock.id {
                    return &lock.description;
                }
            }
        }
        ""
    }
}

const LCS_LOCK_URI: &str = "/iris/lut/lcs_lock";

impl AncillaryData for LcsArrayAnc {
    type Primary = LcsArray;

    /// Get URI iterator
    fn uri_iter(
        &self,
        _pri: &LcsArray,
        _view: View,
    ) -> Box<dyn Iterator<Item = Uri>> {
        Box::new(once(LCS_LOCK_URI.into()))
    }

    /// Put ancillary data
    fn set_data(
        &mut self,
        _pri: &LcsArray,
        _uri: Uri,
        data: JsValue,
    ) -> Result<bool> {
        self.locks = Some(serde_wasm_bindgen::from_value(data)?);
        Ok(false)
    }
}

impl LcsArray {
    /// Convert to Compact HTML
    fn to_html_compact(&self, anc: &LcsArrayAnc) -> String {
        let name = HtmlStr::new(self.name());
        let lock = anc.lock(self);
        format!(
            "<div class='title row'>\
              <span>{name}</span>\
              <span>{lock}</span>\
            </div>"
        )
    }

    /// Convert to Control HTML
    fn to_html_control(&self, anc: &LcsArrayAnc) -> String {
        let title = self.title(View::Control);
        let lock = anc.lock(self);
        format!(
            "{title}\
            <div class='row'>\
              <span class='info'>{lock}</span>\
            </div>"
        )
    }
}

impl Card for LcsArray {
    type Ancillary = LcsArrayAnc;

    /// Display name
    const DNAME: &'static str = "🡇🡇 LCS Array";

    /// Get the resource
    fn res() -> Res {
        Res::LcsArray
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
    fn is_match(&self, search: &str, anc: &LcsArrayAnc) -> bool {
        self.name.contains_lower(search) || anc.lock(self).contains(search)
    }

    /// Convert to HTML view
    fn to_html(&self, view: View, anc: &LcsArrayAnc) -> String {
        match view {
            View::Create => self.to_html_create(anc),
            View::Control => self.to_html_control(anc),
            _ => self.to_html_compact(anc),
        }
    }

    /// Get changed fields from Setup form
    fn changed_fields(&self) -> String {
        let fields = Fields::new();
        fields.into_value().to_string()
    }
}
