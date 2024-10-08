\set ON_ERROR_STOP

SET SESSION AUTHORIZATION 'tms';
BEGIN;

SELECT iris.update_version('5.59.0', '5.60.0');

-- temp function
CREATE FUNCTION action_plan_hashtag(TEXT) RETURNS TEXT AS $aph$
DECLARE
    action_plan ALIAS FOR $1;
    words TEXT[];
    word TEXT;
    res TEXT := '#';
BEGIN
    words = regexp_split_to_array(
        regexp_replace(action_plan, '(\d)_(\d)', '\1s\2', 'g'),
        '_'
    );
    FOREACH word IN ARRAY words
    LOOP
        IF length(word) > 2 THEN
            res = res || initcap(word);
        ELSE
            res = res || upper(word);
        END IF;
    END LOOP;
    RETURN res;
END;
$aph$ LANGUAGE plpgsql;

-- Concatenate meter action hashtags to notes fields
WITH cte AS (
    SELECT ramp_meter, string_agg(
        DISTINCT(action_plan_hashtag(action_plan)),
        ' ' ORDER BY action_plan_hashtag(action_plan)
    ) hashtags
    FROM iris.meter_action
    GROUP BY ramp_meter
)
UPDATE iris.ramp_meter m
SET notes = concat_ws(e'\n\n', notes, h.hashtags)
FROM cte h
WHERE h.ramp_meter = m.name;

-- Concatenate beacon action hashtags to notes fields
WITH cte AS (
    SELECT beacon, string_agg(
        DISTINCT(action_plan_hashtag(action_plan)),
        ' ' ORDER BY action_plan_hashtag(action_plan)
    ) hashtags
    FROM iris.beacon_action
    GROUP BY beacon
)
UPDATE iris.beacon b
SET notes = concat_ws(e'\n\n', notes, h.hashtags)
FROM cte h
WHERE h.beacon = b.name;

ALTER TABLE iris.sign_config DROP CONSTRAINT sign_config_module_width_check;
ALTER TABLE iris.sign_config ADD CONSTRAINT sign_config_check
    CHECK (
        module_width > 0 AND
        (pixel_width % module_width) = 0
    );

ALTER TABLE iris.sign_config DROP CONSTRAINT sign_config_module_height_check;
ALTER TABLE iris.sign_config ADD CONSTRAINT sign_config_check1
    CHECK (
        module_height > 0 AND
        (pixel_height % module_height) = 0
    );

-- Replace camera/dms/lane/meter actions with device_action
DROP VIEW dms_toll_zone_view;
DROP VIEW dms_action_view;
DROP VIEW meter_action_view;

INSERT INTO iris.resource_type (name, base) VALUES ('device_action', false);
UPDATE iris.privilege SET type_n = 'device_action' WHERE type_n = 'dms_action';

DELETE FROM iris.privilege
    WHERE type_n = 'beacon_action'
       OR type_n = 'camera_action'
       OR type_n = 'lane_action'
       OR type_n = 'meter_action';

DELETE FROM iris.resource_type
    WHERE name = 'beacon_action'
       OR name = 'camera_action'
       OR name = 'dms_action'
       OR name = 'lane_action'
       OR name = 'meter_action';

CREATE TABLE iris.device_action (
    name VARCHAR(30) PRIMARY KEY,
    action_plan VARCHAR(16) NOT NULL REFERENCES iris.action_plan,
    phase VARCHAR(12) NOT NULL REFERENCES iris.plan_phase,
    hashtag VARCHAR(16) NOT NULL,
    msg_pattern VARCHAR(20) REFERENCES iris.msg_pattern,
    msg_priority INTEGER NOT NULL,

    CONSTRAINT hashtag_ck CHECK (hashtag ~ '^#[A-Za-z0-9]+$')
);

INSERT INTO iris.device_action (
    name, action_plan, phase, hashtag, msg_pattern, msg_priority
)
SELECT name, action_plan, phase, dms_hashtag, msg_pattern, msg_priority
FROM iris.dms_action;

INSERT INTO iris.device_action (
    name, action_plan, phase, hashtag, msg_priority
)
SELECT DISTINCT ON (action_plan, phase)
    name, action_plan, phase, action_plan_hashtag(action_plan), 1
FROM iris.meter_action
ORDER BY action_plan, phase, name;

INSERT INTO iris.device_action (
    name, action_plan, phase, hashtag, msg_priority
)
SELECT DISTINCT ON (action_plan, phase)
    concat(name, '0'), action_plan, phase, action_plan_hashtag(action_plan), 1
FROM iris.beacon_action
ORDER BY action_plan, phase, name;

CREATE VIEW device_action_view AS
    SELECT name, action_plan, phase, hashtag, msg_pattern, msg_priority
    FROM iris.device_action;
GRANT SELECT ON device_action_view TO PUBLIC;

CREATE VIEW dms_action_view AS
    SELECT h.name AS dms, action_plan, phase, h.hashtag, msg_pattern,
           msg_priority
    FROM iris.device_action da
    JOIN iris.hashtag h ON h.hashtag = da.hashtag AND resource_n = 'dms';
GRANT SELECT ON dms_action_view TO PUBLIC;

CREATE VIEW dms_toll_zone_view AS
    SELECT dms, hashtag, tz.state, toll_zone, action_plan, da.msg_pattern
    FROM dms_action_view da
    JOIN iris.msg_pattern mp
    ON da.msg_pattern = mp.name
    JOIN iris.msg_pattern_toll_zone tz
    ON da.msg_pattern = tz.msg_pattern;
GRANT SELECT ON dms_toll_zone_view TO PUBLIC;

CREATE VIEW meter_action_view AS
    SELECT h.name AS ramp_meter, da.action_plan, ta.phase, h.hashtag,
           msg_pattern, time_of_day, day_plan, sched_date
    FROM iris.device_action da
    JOIN iris.hashtag h ON h.hashtag = da.hashtag AND resource_n = 'ramp_meter'
    JOIN iris.action_plan ap ON da.action_plan = ap.name
    LEFT JOIN iris.time_action ta ON ta.action_plan = ap.name
    WHERE active = true
    ORDER BY ramp_meter, time_of_day;
GRANT SELECT ON meter_action_view TO PUBLIC;

DROP TABLE iris.beacon_action;
DROP TABLE iris.camera_action;
DROP TABLE iris.dms_action;
DROP TABLE iris.lane_action;
DROP TABLE iris.meter_action;

DROP FUNCTION action_plan_hashtag(TEXT);

-- Add lane marking hashtag trigger
CREATE FUNCTION iris.lane_marking_hashtag() RETURNS TRIGGER AS
    $lane_marking_hashtag$
BEGIN
    IF (NEW.notes IS DISTINCT FROM OLD.notes) THEN
        IF (TG_OP != 'INSERT') THEN
            DELETE FROM iris.hashtag
            WHERE resource_n = 'lane_marking' AND name = OLD.name;
        END IF;
        IF (TG_OP != 'DELETE') THEN
            INSERT INTO iris.hashtag (resource_n, name, hashtag)
            SELECT 'lane_marking', NEW.name, iris.parse_tags(NEW.notes);
        END IF;
    END IF;
    RETURN NULL; -- AFTER trigger return is ignored
END;
$lane_marking_hashtag$ LANGUAGE plpgsql;

CREATE TRIGGER lane_marking_hashtag_trig
    AFTER INSERT OR UPDATE OR DELETE ON iris._lane_marking
    FOR EACH ROW EXECUTE FUNCTION iris.lane_marking_hashtag();

-- Re-introduce RWIS auto max distance
INSERT INTO iris.system_attribute (name, value) VALUES
    ('rwis_auto_max_dist_miles', '1.0');

COMMIT;
