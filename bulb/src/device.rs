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
use crate::controller::Controller;
use crate::error::Result;
use crate::fetch::Uri;
use crate::item::ItemState;
use crate::util::HtmlStr;
use std::borrow::Cow;
use std::iter::{empty, once};
use std::marker::PhantomData;
use wasm_bindgen::JsValue;

/// Device requests
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[allow(dead_code)]
pub enum DeviceReq {
    NoRequest,
    QueryConfiguration,
    QuerySettings,
    SendSettings,
    QueryMessage,
    QueryStatus,
    QueryPixelFailures,
    TestPixels,
    TestFans,
    TestLamps,
    BrightnessGood,
    BrightnessTooDim,
    BrightnessTooBright,
    ResetDevice,
    ResetStatus,
    QueryGpsLocation,
    DisableSystem,
    CameraFocusStop,
    CameraFocusNear,
    CameraFocusFar,
    CameraFocusManual,
    CameraFocusAuto,
    CameraIrisStop,
    CameraIrisClose,
    CameraIrisOpen,
    CameraIrisManual,
    CameraIrisAuto,
    CameraWiperOneShot,
    CameraWasher,
    CameraPowerOn,
    CameraPowerOff,
    CameraMenuOpen,
    CameraMenuEnter,
    CameraMenuCancel,
}

/// Device resource for controller IO
pub trait Device {
    /// Get controller
    fn controller(&self) -> Option<&str> {
        None
    }
}

/// Ancillary controller IO device data
#[derive(Debug, Default)]
pub struct DeviceAnc<D> {
    pri: PhantomData<D>,
    pub controllers: Option<Vec<Controller>>,
    pub controller: Option<Controller>,
}

impl<D: Device> DeviceAnc<D> {
    /// Find controller
    fn controller(&self, pri: &D) -> Option<&Controller> {
        if let Some(ctrl) = &self.controller {
            return Some(ctrl);
        }
        if let (Some(ctrl), Some(controllers)) =
            (pri.controller(), &self.controllers)
        {
            for c in controllers {
                if c.name == ctrl {
                    return Some(c);
                }
            }
        }
        None
    }

    /// Make controller button
    fn controller_button(&self) -> String {
        match &self.controller {
            Some(ctrl) => ctrl.button_html(),
            None => "<span></span>".into(),
        }
    }

    /// Make controller row as HTML
    pub fn controller_html(&self) -> String {
        let ctl_btn = self.controller_button();
        let controller = match &self.controller {
            Some(c) => HtmlStr::new(c.name()),
            None => HtmlStr::new(Cow::Borrowed("")),
        };
        format!(
            "<div class='row'>\
              <label for='controller'>Controller</label>\
              <input id='controller' maxlength='20' size='20' \
                     value='{controller}'>\
              {ctl_btn}\
            </div>"
        )
    }

    /// Get item state
    pub fn item_state(&self, pri: &D) -> ItemState {
        self.controller(pri)
            .map_or(ItemState::Inactive, |c| c.item_state())
    }
}

const CONTROLLER_URI: &str = "/iris/api/controller";

impl<D: Device> AncillaryData for DeviceAnc<D> {
    type Primary = D;

    /// Get URI iterator
    fn uri_iter(&self, pri: &D, view: View) -> Box<dyn Iterator<Item = Uri>> {
        match (view, &pri.controller()) {
            (View::Search, _) => Box::new(once(CONTROLLER_URI.into())),
            (
                View::Hidden | View::Compact | View::Control | View::Setup,
                Some(ctrl),
            ) => {
                let mut uri = Uri::from("/iris/api/controller/");
                uri.push(ctrl);
                Box::new(once(uri))
            }
            _ => Box::new(empty()),
        }
    }

    /// Put ancillary data
    fn set_data(&mut self, _pri: &D, uri: Uri, data: JsValue) -> Result<bool> {
        if uri.as_str() == CONTROLLER_URI {
            self.controllers = Some(serde_wasm_bindgen::from_value(data)?);
        } else {
            self.controller = Some(serde_wasm_bindgen::from_value(data)?);
        }
        Ok(false)
    }
}
