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
use crate::cio::{ControllerIo, ControllerIoAnc};
use crate::error::Result;
use crate::geoloc::{Loc, LocAnc};
use crate::item::ItemState;
use crate::start::fly_map_item;
use crate::util::{ContainsLower, Fields, Input, TextArea, opt_ref};
use hatmil::Html;
use humantime::format_duration;
use mag::length::{m, mm};
use mag::temp::DegC;
use mag::time::s;
use resources::Res;
use serde::Deserialize;
use std::borrow::Cow;
use std::time::Duration;
use wasm_bindgen::JsValue;

/// Display Units
type TempUnit = mag::temp::DegF;
type DistUnit = mag::length::mi;
type DepthUnit = mag::length::In;
type SpeedUnit = mag::time::h;

/// Barometer conversion
const PASCALS_TO_IN_HG: f32 = 0.0002953;

/// Pavement sensor settings
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct PavementSettings {
    location: Option<String>,
    pavement_type: Option<String>,
    height: Option<f32>,
    exposure: Option<u32>,
    sensor_type: Option<String>,
}

/// Sub-surface sensor settings
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct SubSurfaceSettings {
    location: Option<String>,
    sub_surface_type: Option<String>,
    depth: Option<f32>,
}

/// Weather Sensor Settings
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct WeatherSettings {
    pavement_sensor: Option<Vec<PavementSettings>>,
    sub_surface_sensor: Option<Vec<SubSurfaceSettings>>,
}

/// Air temp data
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct AirTemp {
    air_temp: Option<f32>,
}

/// Wind sensor data
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct WindData {
    avg_speed: Option<f32>,
    avg_direction: Option<u32>,
    spot_speed: Option<f32>,
    spot_direction: Option<u32>,
    gust_speed: Option<f32>,
    gust_direction: Option<u32>,
}

/// Pavement sensor data
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct PavementData {
    surface_status: Option<String>,
    sensor_error: Option<String>,
    surface_temp: Option<f32>,
    pavement_temp: Option<f32>,
    freeze_point: Option<f32>,
    ice_or_water_depth: Option<f32>,
    salinity: Option<u32>,
    black_ice_signal: Option<String>,
    friction: Option<u32>,
}

/// Sub-surface sensor data
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct SubSurfaceData {
    sensor_error: Option<String>,
    temp: Option<f32>,
    moisture: Option<u32>,
}

/// Weather Sensor Data
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct WeatherData {
    visibility_situation: Option<String>,
    visibility: Option<u32>,
    relative_humidity: Option<u32>,
    atmospheric_pressure: Option<u32>,
    temperature_sensor: Option<Vec<AirTemp>>,
    dew_point_temp: Option<f32>,
    wet_bulb_temp: Option<f32>,
    /// Minimum air temp in last 24 hours (first sensor)
    min_air_temp: Option<f32>,
    /// Maximum air temp in last 24 hours (first sensor)
    max_air_temp: Option<f32>,
    precip_situation: Option<String>,
    precip_1_hour: Option<f32>,
    precip_3_hours: Option<f32>,
    precip_6_hours: Option<f32>,
    precip_12_hours: Option<f32>,
    precip_24_hours: Option<f32>,
    wind_sensor: Option<Vec<WindData>>,
    cloud_situation: Option<String>,
    total_sun: Option<u32>,
    solar_radiation: Option<i32>,
    instantaneous_terrestrial_radiation: Option<i32>,
    instantaneous_solar_radiation: Option<i32>,
    total_radiation: Option<i32>,
    total_radiation_period: Option<u32>,
    pavement_sensor: Option<Vec<PavementData>>,
    sub_surface_sensor: Option<Vec<SubSurfaceData>>,
}

/// Weather Sensor
#[derive(Debug, Default, Deserialize, PartialEq)]
pub struct WeatherSensor {
    pub name: String,
    pub location: Option<String>,
    pub site_id: Option<String>,
    pub alt_id: Option<String>,
    pub notes: Option<String>,
    pub geo_loc: Option<String>,
    pub controller: Option<String>,
    // secondary attributes
    pub pin: Option<u32>,
    pub settings: Option<WeatherSettings>,
    pub sample: Option<WeatherData>,
    pub sample_time: Option<String>,
}

/// Weather sensor ancillary data
#[derive(Default)]
pub struct WeatherSensorAnc {
    cio: ControllerIoAnc<WeatherSensor>,
    loc: LocAnc<WeatherSensor>,
}

impl AncillaryData for WeatherSensorAnc {
    type Primary = WeatherSensor;

    /// Construct ancillary weather sensor data
    fn new(pri: &WeatherSensor, view: View) -> Self {
        let cio = ControllerIoAnc::new(pri, view);
        let mut loc = LocAnc::new(pri, view);
        // Need geoloc to fly to location on map
        if let (View::Status, Some(nm)) = (view, pri.geoloc()) {
            loc.assets
                .push(Asset::GeoLoc(nm.to_string(), Res::WeatherSensor));
        }
        WeatherSensorAnc { cio, loc }
    }

    /// Get next asset to fetch
    fn asset(&mut self) -> Option<Asset> {
        self.cio.assets.pop().or_else(|| self.loc.assets.pop())
    }

    /// Set asset value
    fn set_asset(
        &mut self,
        pri: &WeatherSensor,
        asset: Asset,
        value: JsValue,
    ) -> Result<()> {
        if let Asset::Controllers = asset {
            self.cio.set_asset(pri, asset, value)
        } else {
            self.loc.set_asset(pri, asset, value)
        }
    }
}

/// Get visibility situation string (from NTCIP 1204)
fn vis_situation(situation: &str) -> &'static str {
    match situation {
        "other" => "⛆ other visibility anomaly",
        "clear" => "🔭 clear",
        "fogNotPatchy" => "🌫️ fog",
        "patchyFog" => "🌁 patchy fog",
        "blowingSnow" => "❄️ snow",
        "smoke" => "🚬 smoke",
        "seaSpray" => "💦 sea spray",
        "vehicleSpray" => "💦 spray",
        "blowingDustOrSand" => "💨 dust",
        "sunGlare" => "🕶️ sun glare",
        "swarmOfInsects" => "🦗 swarm", // seriously?!?
        _ => "Atmosphere",
    }
}

/// Get direction and arrow from degrees
fn dir_arrow(deg: u32) -> Option<&'static str> {
    match deg {
        // 0° ± 22.5
        0..=22 | 338..=360 => Some("N ↓"),
        // 45° ±22.5
        23..=67 => Some("NE ↙"),
        // 90° ±22.5
        68..=112 => Some("E ←"),
        // 135° ±22.5
        113..=157 => Some("SE ↖"),
        // 180° ±22.5
        158..=202 => Some("S ↑"),
        // 225° ±22.5
        203..=247 => Some("SW ↗"),
        // 270° ±22.5
        248..=292 => Some("W →"),
        // 315° ±22.5
        293..=337 => Some("NW ↘"),
        _ => None,
    }
}

/// Build wind direction HTML
fn wind_dir_html(deg: u32, html: &mut Html) {
    html.span().class("info");
    if let Some(arrow) = dir_arrow(deg) {
        html.text(arrow);
    }
    html.end(); /* span */
}

/// Format temperature quantity
fn format_temp(temp: f32) -> String {
    let temp = (f64::from(temp) * DegC).to::<TempUnit>();
    format!("{temp:.1}")
}

/// Get precipitation situation string (from NTCIP 1204)
fn precip_situation(situation: &str) -> &'static str {
    match situation {
        "noPrecipitation" => "🌂 No Precipitation",
        "unidentifiedSlight" => "🌧️ Slight precipitation",
        "unidentifiedModerate" => "🌧️ Moderate precipitation",
        "unidentifiedHeavy" => "🌧️ Heavy precipitation",
        "snowSlight" => "🌨️ Slight snow",
        "snowModerate" => "🌨️ Moderate snow",
        "snowHeavy" => "🌨️ Heavy snow",
        "rainSlight" => "🌧️ Slight rain",
        "rainModerate" => "🌧️ Moderate rain",
        "rainHeavy" => "🌧️ Heavy rain",
        "frozenPrecipitationSlight" => "🧊 Slight sleet",
        "frozenPrecipitationModerate" => "🧊 Moderate sleet",
        "frozenPrecipitationHeavy" => "🧊 Heavy sleet",
        _ => "🌧️ Precipitation",
    }
}

/// Format depth quantity
fn format_depth(depth_mm: f32) -> String {
    let depth = (f64::from(depth_mm) * mm).to::<DepthUnit>();
    format!("{depth:.2}")
}

/// Format wind speed quantity
fn format_speed(speed: f32) -> String {
    let speed = (f64::from(speed) * m / s).to::<DistUnit, SpeedUnit>();
    format!("{speed:.0}")
}

/// Get cloud situation string (from NTCIP 1204)
fn cloud_situation(situation: &str) -> &'static str {
    match situation {
        "overcast" => "☁️ Overcast",
        "cloudy" => "🌥️ Mostly cloudy",
        "partlyCloudy" => "⛅ Partly cloudy",
        "mostlyClear" => "🌤️ Mostly clear",
        "clear" => "☀️ Clear",
        _ => "☁️ Unknown",
    }
}

impl WeatherData {
    /// Check if atmospheric data exists
    fn atmospheric_exists(&self) -> bool {
        self.visibility_situation.is_some()
            || self.visibility.is_some()
            || self.relative_humidity.is_some()
    }

    /// Check if temperature data exists
    fn temperature_exists(&self) -> bool {
        self.temperature_sensor.is_some()
            || self.dew_point_temp.is_some()
            || self.wet_bulb_temp.is_some()
            || self.min_air_temp.is_some()
            || self.max_air_temp.is_some()
    }

    /// Check if precip data exists
    fn precip_exists(&self) -> bool {
        self.precip_situation.is_some()
            || self.precip_1_hour.is_some()
            || self.precip_3_hours.is_some()
            || self.precip_6_hours.is_some()
            || self.precip_12_hours.is_some()
            || self.precip_24_hours.is_some()
    }

    /// Check if radiation data exists
    fn radiation_exists(&self) -> bool {
        self.cloud_situation.is_some()
            || self.total_sun.is_some()
            || self.solar_radiation.is_some()
            || self.instantaneous_terrestrial_radiation.is_some()
            || self.instantaneous_solar_radiation.is_some()
            || self.total_radiation.is_some()
    }

    /// Build weather data HTML
    fn build_html(&self, settings: Option<&WeatherSettings>, html: &mut Html) {
        if self.temperature_exists() {
            self.temperature_html(html);
        }
        if self.atmospheric_exists() {
            self.atmospheric_html(html);
        }
        if self.radiation_exists() {
            self.radiation_html(html);
        }
        if let Some(wind_sensor) = &self.wind_sensor {
            self.wind_html(wind_sensor, html);
        }
        if self.precip_exists() {
            self.precipitation_html(html);
        }
        if let Some(data) = &self.pavement_sensor {
            pavement_html(pavement_settings(settings), data, html);
        }
        if let Some(data) = &self.sub_surface_sensor {
            sub_surface_html(sub_surface_settings(settings), data, html);
        }
    }

    /// Get the average air temperature
    fn temperature_avg(&self) -> Option<f32> {
        match &self.temperature_sensor {
            Some(sensors) => sensors
                .iter()
                .filter_map(|at| at.air_temp.map(|t| (t, 1)))
                .reduce(|acc, (t, c)| (t + acc.0, c + acc.1))
                .map(|(total, count)| total / count as f32),
            None => None,
        }
    }

    /// Build temperature HTML
    fn temperature_html(&self, html: &mut Html) {
        html.details().summary().text("🌡️ ");
        if let Some(avg) = self.temperature_avg() {
            html.text(format_temp(avg));
        }
        html.end(); /* summary */
        html.ul();
        if let Some(sensor) = &self.temperature_sensor
            && sensor.len() > 1
        {
            for (i, temp) in sensor.iter().enumerate() {
                html.li().text("#").text(i).text(" Air ");
                if let Some(temp) = temp.air_temp {
                    html.text(format_temp(temp));
                }
                html.end(); /* li */
            }
        }
        if let Some(temp) = self.min_air_temp {
            html.li().text("24h low ").text(format_temp(temp)).end();
        }
        if let Some(temp) = self.max_air_temp {
            html.li().text("24h high ").text(format_temp(temp)).end();
        }
        if let Some(temp) = self.dew_point_temp {
            html.li().text("Dew point ").text(format_temp(temp)).end();
        }
        if let Some(temp) = self.wet_bulb_temp {
            html.li().text("Wet bulb ").text(format_temp(temp)).end();
        }
        html.end(); /* ul */
        html.end(); /* details */
    }

    /// Build atmospheric HTML
    fn atmospheric_html(&self, html: &mut Html) {
        html.details().summary();
        html.text(vis_situation(
            self.visibility_situation.as_deref().unwrap_or("unknown"),
        ));
        html.end(); /* summary */
        html.ul();
        if let Some(visibility) = self.visibility {
            let v = (f64::from(visibility) * m).to::<DistUnit>();
            html.li().text("Visibility ").text(format!("{v:.1}")).end();
        }
        if let Some(rh) = self.relative_humidity {
            html.li().text("RH ").text(rh).text("%").end();
        }
        if let Some(p) = self.atmospheric_pressure {
            let p = (p as f32) * PASCALS_TO_IN_HG;
            html.li()
                .text("Barometer ")
                .text(format!("{p:.2}"))
                .text(" inHg")
                .end();
        }
        html.end(); /* ul */
        html.end(); /* details */
    }

    /// Build radiation data HTML
    fn radiation_html(&self, html: &mut Html) {
        html.details().summary();
        match self.cloud_situation.as_ref() {
            Some(cs) => html.text(cloud_situation(cs)),
            None => html.text("Sky"),
        };
        html.end(); /* summary */
        html.ul();
        if let Some(sun) = self.total_sun {
            let d = format_duration(Duration::from_secs(60 * u64::from(sun)))
                .to_string();
            html.li().text(d).text(" of sun").end();
        }
        if let Some(r) = &self.solar_radiation {
            html.li().text("Solar radiation: ").text(*r);
            html.text(" J/m²").end();
        }
        if let Some(r) = &self.instantaneous_terrestrial_radiation {
            html.li().text("Instantaneous terrestrial: ");
            html.text(*r).text(" W/m²").end();
        }
        if let Some(r) = &self.instantaneous_solar_radiation {
            html.li().text("Instantaneous solar: ");
            html.text(*r).text(" W/m²").end();
        }
        if let Some(r) = &self.total_radiation {
            html.li().text("Total radiation: ");
            html.text(*r).text(" W/m²").end();
            if let Some(p) = self.total_radiation_period {
                let d =
                    format_duration(Duration::from_secs(p.into())).to_string();
                html.li().text("Total radiation period: ").text(d).end();
            }
        }
        html.end(); /* ul */
        html.end(); /* details */
    }

    /// Build wind data HTML
    fn wind_html(&self, data: &[WindData], html: &mut Html) {
        html.details().summary();
        html.text("🌬️ Wind");
        if let Some(ws) = data.iter().next() {
            if let Some(dir) = ws.avg_direction {
                html.text(" 🧭 ");
                wind_dir_html(dir, html);
            }
            if let Some(speed) = ws.avg_speed {
                html.text(" ");
                html.text(format_speed(speed));
            }
        }
        html.end(); /* summary */
        html.ul();
        for (i, ws) in data.iter().enumerate() {
            let num = if data.len() > 1 {
                Some(format!("#{i} "))
            } else {
                None
            };
            if i > 0 && (ws.avg_direction.is_some() || ws.avg_speed.is_some()) {
                html.li();
                if let Some(num) = &num {
                    html.text(num);
                }
                if let Some(dir) = ws.avg_direction {
                    html.text("Avg 🧭 ");
                    wind_dir_html(dir, html);
                }
                if let Some(speed) = ws.avg_speed {
                    html.text(" ");
                    html.text(format_speed(speed));
                }
                html.end(); /* li */
            }
            if ws.spot_direction.is_some() || ws.spot_speed.is_some() {
                html.li();
                if let Some(num) = &num {
                    html.text(num);
                }
                if let Some(dir) = ws.spot_direction {
                    html.text("Spot 🧭 ");
                    wind_dir_html(dir, html);
                }
                if let Some(speed) = ws.spot_speed {
                    html.text(" ");
                    html.text(format_speed(speed));
                }
                html.end(); /* li */
            }
            if ws.gust_direction.is_some() || ws.gust_speed.is_some() {
                html.li();
                if let Some(num) = &num {
                    html.text(num);
                }
                if let Some(dir) = ws.gust_direction {
                    html.text("Gust 🧭 ");
                    wind_dir_html(dir, html);
                }
                if let Some(speed) = ws.gust_speed {
                    html.text(" ");
                    html.text(format_speed(speed));
                }
                html.end(); /* li */
            }
        }
        html.end(); /* ul */
        html.end(); /* details */
    }

    /// Build precipitation data HTML
    fn precipitation_html(&self, html: &mut Html) {
        html.details().summary();
        html.text(precip_situation(
            self.precip_situation.as_deref().unwrap_or("unknown"),
        ));
        html.end(); /* summary */
        html.ul();
        if let Some(precip) = self.precip_1_hour {
            html.li().text("1h, ").text(format_depth(precip)).end();
        }
        if let Some(precip) = self.precip_3_hours {
            html.li().text("3h, ").text(format_depth(precip)).end();
        }
        if let Some(precip) = self.precip_6_hours {
            html.li().text("6h, ").text(format_depth(precip)).end();
        }
        if let Some(precip) = self.precip_12_hours {
            html.li().text("12h, ").text(format_depth(precip)).end();
        }
        if let Some(precip) = self.precip_24_hours {
            html.li().text("24h, ").text(format_depth(precip)).end();
        }
        html.end(); /* ul */
        html.end(); /* details */
    }
}

/// Get pavement settings
fn pavement_settings(
    settings: Option<&WeatherSettings>,
) -> &[PavementSettings] {
    if let Some(settings) = settings
        && let Some(settings) = &settings.pavement_sensor
    {
        return settings;
    }
    &[]
}

/// Get pavement data as HTML
fn pavement_html(
    settings: &[PavementSettings],
    data: &[PavementData],
    html: &mut Html,
) {
    let len = settings.len().max(data.len());
    for i in 0..len {
        html.details().summary().text("Pavement ");
        if len > 1 {
            html.text(format!("#{i} "));
        };
        if let Some(pd) = data.get(i) {
            if let Some(status) = &pd.surface_status {
                html.text(status);
                if pd.surface_temp.is_some() {
                    html.text(", ");
                }
            }
            if let Some(temp) = pd.surface_temp {
                html.text(format_temp(temp));
            }
        }
        html.end(); /* summary */
        html.ul();
        if let Some(pd) = data.get(i) {
            if let Some(err) = &pd.sensor_error {
                html.li().text(err).text(" error").end();
            }
            if let Some(temp) = pd.pavement_temp {
                html.li().text("Pavement ").text(format_temp(temp)).end();
            }
            if let Some(temp) = pd.freeze_point {
                html.li()
                    .text("Freeze point ")
                    .text(format_temp(temp))
                    .end();
            }
            if let Some(depth_m) = pd.ice_or_water_depth {
                let d = format_depth(depth_m * 1_000.0);
                html.li().text("Water/ice depth ").text(d).end();
            }
            if let Some(salinity) = pd.salinity {
                let sl = salinity;
                html.li().text("Salinity ").text(sl).text(" ppm").end();
            }
            if let Some(signal) = &pd.black_ice_signal {
                html.li().text(signal).end();
            }
            if let Some(friction) = &pd.friction {
                let f = friction;
                html.li()
                    .text("Coef. of friction ")
                    .text(*f)
                    .text("%")
                    .end();
            }
        }
        if let Some(ps) = settings.get(i) {
            if let Some(loc) = &ps.location
                && !loc.trim().is_empty()
            {
                html.li().text(loc).end();
            }
            if let Some(tp) = &ps.pavement_type {
                html.li().text(tp).text(" pavement").end();
            }
            if let Some(tp) = &ps.sensor_type {
                html.li().text("Type: ").text(tp).end();
            }
            if let Some(height) = ps.height {
                let h = format!("{height:.2}");
                html.li().text("Height ").text(h).text(" m").end();
            }
            if let Some(exposure) = ps.exposure {
                let e = exposure;
                html.li().text("Exposure ").text(e).text("%").end();
            }
        }
        html.end(); /* ul */
        html.end(); /* details */
    }
}

/// Get sub-surface settings
fn sub_surface_settings(
    settings: Option<&WeatherSettings>,
) -> &[SubSurfaceSettings] {
    if let Some(settings) = settings
        && let Some(settings) = &settings.sub_surface_sensor
    {
        return settings;
    }
    &[]
}

/// Build sub-surface data HTML
fn sub_surface_html(
    settings: &[SubSurfaceSettings],
    data: &[SubSurfaceData],
    html: &mut Html,
) {
    let len = settings.len().max(data.len());
    for i in 0..len {
        html.details().summary().text("Sub-surface ");
        if len > 1 {
            html.text(format!("#{i} "));
        };
        if let Some(sd) = data.get(i)
            && let Some(temp) = sd.temp
        {
            html.text(format_temp(temp));
        }
        html.end(); /* summary */
        html.ul();
        if let Some(ss) = settings.get(i) {
            if let Some(loc) = &ss.location {
                let loc = loc.trim();
                if !loc.is_empty() {
                    html.li().text(loc).end();
                }
            }
            if let Some(tp) = &ss.sub_surface_type {
                html.li().text("Type: ").text(tp).end();
            }
            if let Some(depth) = ss.depth {
                let d = format!("{depth:.2}");
                html.li().text("Depth ").text(d).text(" m").end();
            }
        }
        if let Some(sd) = data.get(i) {
            if let Some(err) = &sd.sensor_error {
                html.li().text(err).text(" error").end();
            }
            if let Some(moisture) = &sd.moisture {
                let mo = moisture;
                html.li().text("Moisture ").text(*mo).text("%").end();
            }
        }
        html.end(); /* ul */
        html.end(); /* details */
    }
}

impl WeatherSensor {
    /// Convert to Compact HTML
    fn to_html_compact(&self, anc: &WeatherSensorAnc) -> String {
        let mut html = Html::new();
        html.div()
            .class("title row")
            .text(self.name())
            .text(" ")
            .text(anc.cio.item_states(self).to_string())
            .end();
        html.div()
            .class("info fill")
            .text_len(opt_ref(&self.location), 32);
        html.to_string()
    }

    /// Convert to Status HTML
    fn to_html_status(&self, anc: &WeatherSensorAnc) -> String {
        if let Some((lat, lon)) = anc.loc.latlon() {
            fly_map_item(&self.name, lat, lon);
        }
        let mut html = self.title(View::Status);
        html.div().class("row");
        anc.cio.item_states(self).tooltips(&mut html);
        html.end(); /* div */
        html.div().class("row");
        html.span()
            .class("info")
            .text_len(opt_ref(&self.location), 64)
            .end();
        html.end(); /* div */
        html.div().class("row");
        html.span().class("info").text(opt_ref(&self.site_id)).end();
        html.span().class("info").text(opt_ref(&self.alt_id)).end();
        html.end(); /* div */
        if let Some(sample_time) = &self.sample_time {
            html.div().class("row");
            html.span().text("Obs").end();
            html.span().class("info").text(sample_time).end();
            html.end(); /* div */
        }
        if let Some(data) = &self.sample {
            data.build_html(self.settings.as_ref(), &mut html);
        }
        html.to_string()
    }

    /// Convert to Setup HTML
    fn to_html_setup(&self, anc: &WeatherSensorAnc) -> String {
        let mut html = self.title(View::Setup);
        html.div().class("row");
        html.label().r#for("site_id").text("Site ID").end();
        html.input()
            .id("site_id")
            .maxlength(20)
            .size(20)
            .value(opt_ref(&self.site_id));
        html.end(); /* div */
        html.div().class("row");
        html.label().r#for("alt_id").text("Alt ID").end();
        html.input()
            .id("alt_id")
            .maxlength(20)
            .size(20)
            .value(opt_ref(&self.alt_id));
        html.end(); /* div */
        html.div().class("row");
        html.label().r#for("notes").text("Notes").end();
        html.textarea()
            .id("notes")
            .maxlength(64)
            .attr("rows", 2)
            .attr("cols", 26)
            .text(opt_ref(&self.notes))
            .end();
        html.end(); /* div */
        anc.cio.controller_html(self, &mut html);
        anc.cio.pin_html(self.pin, &mut html);
        self.footer_html(true, &mut html);
        html.to_string()
    }
}

impl ControllerIo for WeatherSensor {
    /// Get controller name
    fn controller(&self) -> Option<&str> {
        self.controller.as_deref()
    }
}

impl Loc for WeatherSensor {
    /// Get geo location name
    fn geoloc(&self) -> Option<&str> {
        self.geo_loc.as_deref()
    }
}

impl Card for WeatherSensor {
    type Ancillary = WeatherSensorAnc;

    /// Display name
    const DNAME: &'static str = "🌦️ Weather Sensor";

    /// Get the resource
    fn res() -> Res {
        Res::WeatherSensor
    }

    /// Get all item states
    fn item_states_all() -> &'static [ItemState] {
        &[
            ItemState::Available,
            ItemState::Offline,
            ItemState::Inactive,
        ]
    }

    /// Get the name
    fn name(&self) -> Cow<'_, str> {
        Cow::Borrowed(&self.name)
    }

    /// Set the name
    fn with_name(mut self, name: &str) -> Self {
        self.name = name.to_string();
        self
    }

    /// Get the main item state
    fn item_state_main(&self, anc: &Self::Ancillary) -> ItemState {
        let states = anc.cio.item_states(self);
        if states.contains(ItemState::Inactive) {
            ItemState::Inactive
        } else if states.contains(ItemState::Offline) {
            ItemState::Offline
        } else {
            ItemState::Available
        }
    }

    /// Check if a search string matches
    fn is_match(&self, search: &str, anc: &WeatherSensorAnc) -> bool {
        self.name.contains_lower(search)
            || self.location.contains_lower(search)
            || self.site_id.contains_lower(search)
            || self.alt_id.contains_lower(search)
            || anc.cio.item_states(self).is_match(search)
            || self.notes.contains_lower(search)
    }

    /// Convert to HTML view
    fn to_html(&self, view: View, anc: &WeatherSensorAnc) -> String {
        match view {
            View::Create => self.to_html_create(anc),
            View::Location => anc.loc.to_html_loc(self),
            View::Setup => self.to_html_setup(anc),
            View::Status => self.to_html_status(anc),
            _ => self.to_html_compact(anc),
        }
    }

    /// Get changed fields from Setup form
    fn changed_setup(&self) -> String {
        let mut fields = Fields::new();
        fields.changed_input("site_id", &self.site_id);
        fields.changed_input("alt_id", &self.alt_id);
        fields.changed_text_area("notes", &self.notes);
        fields.changed_input("controller", &self.controller);
        fields.changed_input("pin", self.pin);
        fields.into_value().to_string()
    }

    /// Get changed fields on Location view
    fn changed_location(&self, anc: WeatherSensorAnc) -> String {
        anc.loc.changed_location()
    }
}
