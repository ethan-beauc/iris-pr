// Copyright (C) 2022-2025  Minnesota Department of Transportation
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
use crate::asset::Asset;
use crate::card::{AncillaryData, Card, View};
use crate::error::Result;
use crate::util::{Fields, Input, Select, opt_ref, opt_str};
use hatmil::Html;
use serde::Deserialize;
use std::marker::PhantomData;
use wasm_bindgen::JsValue;

/// Road definitions
#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct Road {
    pub name: String,
    pub abbrev: String,
    pub r_class: u16,
    pub direction: u16,
}

/// Roadway directions
#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct Direction {
    pub id: u16,
    pub direction: String,
    pub dir: String,
}

/// Roadway modifiers
#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct RoadModifier {
    pub id: u16,
    pub modifier: String,
    pub md: String,
}

/// Geo location data
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct GeoLoc {
    pub name: String,
    pub resource_n: String,
    pub roadway: Option<String>,
    pub road_dir: u16,
    pub cross_street: Option<String>,
    pub cross_dir: u16,
    pub cross_mod: u16,
    pub landmark: Option<String>,
    pub lat: Option<f64>,
    pub lon: Option<f64>,
}

/// Location resource
pub trait Loc {
    /// Get geo location name
    fn geoloc(&self) -> Option<&str> {
        None
    }
}

/// Ancillary location data
#[derive(Debug, Default)]
pub struct LocAnc<L> {
    pri: PhantomData<L>,
    pub assets: Vec<Asset>,
    geoloc: Option<GeoLoc>,
    roads: Vec<Road>,
    directions: Vec<Direction>,
    modifiers: Vec<RoadModifier>,
}

impl<L> AncillaryData for LocAnc<L>
where
    L: Loc + Card,
{
    type Primary = L;

    /// Construct ancillary location data
    fn new(pri: &L, view: View) -> Self {
        let mut assets = Vec::new();
        match (view, pri.geoloc()) {
            (View::Control, Some(nm)) => {
                assets.push(Asset::GeoLoc(nm.to_string(), L::res()));
            }
            (View::Location, Some(nm)) => {
                assets.push(Asset::GeoLoc(nm.to_string(), L::res()));
                assets.push(Asset::Directions);
                assets.push(Asset::Roads);
                assets.push(Asset::RoadModifiers);
            }
            _ => (),
        }
        LocAnc {
            pri: PhantomData,
            assets,
            geoloc: None,
            roads: Vec::new(),
            directions: Vec::new(),
            modifiers: Vec::new(),
        }
    }

    /// Get next asset to fetch
    fn asset(&mut self) -> Option<Asset> {
        self.assets.pop()
    }

    /// Set asset value
    fn set_asset(
        &mut self,
        _pri: &L,
        asset: Asset,
        value: JsValue,
    ) -> Result<()> {
        match asset {
            Asset::GeoLoc(_nm, _assoc) => {
                self.geoloc = Some(serde_wasm_bindgen::from_value(value)?);
            }
            Asset::Directions => {
                self.directions = serde_wasm_bindgen::from_value(value)?;
            }
            Asset::RoadModifiers => {
                self.modifiers = serde_wasm_bindgen::from_value(value)?;
            }
            Asset::Roads => {
                self.roads = serde_wasm_bindgen::from_value(value)?;
            }
            _ => unreachable!(),
        }
        Ok(())
    }
}

impl<L> LocAnc<L> {
    /// Get lat/lon of location
    pub fn latlon(&self) -> Option<(f64, f64)> {
        match &self.geoloc {
            Some(geoloc) => match (geoloc.lat, geoloc.lon) {
                (Some(lat), Some(lon)) => Some((lat, lon)),
                _ => None,
            },
            None => None,
        }
    }

    /// Build roads HTML
    fn roads_html(&self, id: &str, groad: Option<&str>, html: &mut Html) {
        html.select().id(id);
        html.option().end(); /* empty */
        for road in &self.roads {
            let option = html.option();
            if let Some(groad) = groad
                && groad == road.name
            {
                option.attr_bool("selected");
            }
            html.text(&road.name).end();
        }
        html.end(); /* select */
    }

    /// Build road directions HTML
    fn directions_html(&self, id: &str, dir: u16, html: &mut Html) {
        html.select().id(id);
        for direction in &self.directions {
            let option = html.option().value(direction.id);
            if dir == direction.id {
                option.attr_bool("selected");
            }
            html.text(&direction.direction).end();
        }
        html.end(); /* select */
    }

    /// Build road modifiers HTML
    fn modifiers_html(&self, md: u16, html: &mut Html) {
        html.select().id("cross_mod");
        for modifier in &self.modifiers {
            let option = html.option().value(modifier.id);
            if md == modifier.id {
                option.attr_bool("selected");
            }
            html.text(&modifier.modifier).end();
        }
        html.end(); /* select */
    }

    /// Convert to Location HTML
    pub fn to_html_loc<C>(&self, card: &C) -> String
    where
        C: Card,
    {
        let mut html = card.title(View::Location);
        match &self.geoloc {
            Some(geoloc) => self.location_html(geoloc, &mut html),
            None => {
                html.span().text("Error: missing geo_loc!").end();
            }
        };
        card.footer_html(false, &mut html);
        html.to_string()
    }

    /// Build Location HTML
    fn location_html(&self, loc: &GeoLoc, html: &mut Html) {
        html.div().class("row");
        html.label().r#for("roadway").text("Roadway").end();
        self.roads_html("roadway", loc.roadway.as_deref(), html);
        self.directions_html("road_dir", loc.road_dir, html);
        html.end(); /* div */
        html.div().class("row");
        html.label().r#for("cross_street").text(" ").end();
        self.modifiers_html(loc.cross_mod, html);
        self.roads_html("cross_street", loc.cross_street.as_deref(), html);
        self.directions_html("cross_dir", loc.cross_dir, html);
        html.end(); /* div */
        html.div().class("row");
        html.label().r#for("landmark").text("Landmark").end();
        html.input()
            .id("landmark")
            .maxlength(22)
            .size(24)
            .value(opt_ref(&loc.landmark));
        html.end(); /* div */
        html.div().class("row");
        html.label().r#for("lat").text("Latitude").end();
        html.input()
            .id("lat")
            .r#type("number")
            .attr("step", "0.00001")
            .attr("inputmode", "decimal")
            .value(opt_str(loc.lat));
        html.end(); /* div */
        html.div().class("row");
        html.label().r#for("lon").text("Longitude").end();
        html.input()
            .id("lon")
            .r#type("number")
            .attr("step", "0.00001")
            .attr("inputmode", "decimal")
            .value(opt_str(loc.lon));
        html.end(); /* div */
    }

    /// Get changed fields from Location form
    pub fn changed_location(&self) -> String {
        match &self.geoloc {
            Some(geoloc) => geoloc.changed_location(),
            None => String::new(),
        }
    }
}

impl GeoLoc {
    /// Get changed fields from Location form
    fn changed_location(&self) -> String {
        let mut fields = Fields::new();
        fields.changed_select("roadway", &self.roadway);
        fields.changed_select("road_dir", self.road_dir);
        fields.changed_select("cross_street", &self.cross_street);
        fields.changed_select("cross_mod", self.cross_mod);
        fields.changed_select("cross_dir", self.cross_dir);
        fields.changed_input("landmark", &self.landmark);
        fields.changed_input("lat", self.lat);
        fields.changed_input("lon", self.lon);
        fields.into_value().to_string()
    }
}
