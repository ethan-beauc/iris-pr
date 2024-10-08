// permission.rs
//
// Copyright (C) 2021-2024  Minnesota Department of Transportation
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
use crate::database::Database;
use crate::error::{Error, Result};
use crate::query;
use crate::sonar::Name;
use serde::{Deserialize, Serialize};
use serde_json::map::Map;
use serde_json::Value;
use tokio_postgres::Row;

/// Create one permission
const INSERT_ONE: &str = "\
  INSERT INTO iris.permission (role, base_resource, access_level) \
  VALUES ($1, $2, $3)";

/// Update one permission
const UPDATE_ONE: &str = "\
  UPDATE iris.permission \
  SET (role, base_resource, hashtag, access_level) = ($2, $3, $4, $5) \
  WHERE name = $1";

/// Delete one permission
const DELETE_ONE: &str = "\
  DELETE \
  FROM iris.permission \
  WHERE name = $1";

/// Query access permissions for a user
const ACCESS_BY_USER: &str = "\
  SELECT p.name, p.role, p.base_resource, p.hashtag, p.access_level \
  FROM iris.user_id u \
  JOIN iris.role r ON u.role = r.name \
  JOIN iris.permission p ON p.role = r.name \
  WHERE u.enabled = true \
    AND r.enabled = true \
    AND u.name = $1 \
  ORDER BY p.base_resource, p.hashtag";

/// Query permission for a user / resource (no hashtag)
const QUERY_PERMISSION: &str = "\
  SELECT p.name, p.role, p.base_resource, p.hashtag, p.access_level \
  FROM iris.permission p \
  JOIN iris.role r ON p.role = r.name \
  JOIN iris.user_id u ON u.role = r.name \
  WHERE r.enabled = true \
    AND u.enabled = true \
    AND u.name = $1 \
    AND p.base_resource = $2 \
    AND p.hashtag IS NULL";

/// Query permission for a user / resource / hashtag
const QUERY_PERMISSION_TAG: &str = "\
  SELECT p.name, p.role, p.base_resource, p.hashtag, p.access_level \
  FROM iris.permission p \
  JOIN iris.role r ON p.role = r.name \
  JOIN iris.user_id u ON u.role = r.name \
  WHERE r.enabled = true \
    AND u.enabled = true \
    AND u.name = $1 \
    AND p.base_resource = $2 \
    AND (\
      p.hashtag IS NULL OR \
      p.hashtag IN (\
        SELECT hashtag \
        FROM iris.hashtag h \
        WHERE h.resource_n = p.base_resource \
          AND h.name = $3 \
      )\
    ) \
  ORDER BY access_level DESC \
  LIMIT 1";

/// Permission
#[derive(Clone, Debug, Eq, PartialEq, Deserialize, Serialize)]
pub struct Permission {
    pub name: String,
    pub role: String,
    pub base_resource: String,
    pub hashtag: Option<String>,
    pub access_level: i32,
}

impl Permission {
    fn from_row(row: Row) -> Self {
        Permission {
            name: row.get(0),
            role: row.get(1),
            base_resource: row.get(2),
            hashtag: row.get(3),
            access_level: row.get(4),
        }
    }
}

/// Get one permission by name
pub async fn get_one(db: &Database, name: &str) -> Result<Permission> {
    let client = db.client().await?;
    let row = client
        .query_one(query::PERMISSION_ONE, &[&name])
        .await
        .map_err(|_e| Error::NotFound)?;
    Ok(Permission::from_row(row))
}

/// Get permissions for a user
pub async fn get_by_user(db: &Database, user: &str) -> Result<Vec<Permission>> {
    let mut perms = Vec::new();
    let client = db.client().await?;
    for row in client.query(ACCESS_BY_USER, &[&user]).await? {
        perms.push(Permission::from_row(row));
    }
    Ok(perms)
}

/// Create a permission by role/resource
pub async fn post_role_res(
    db: &Database,
    role: &str,
    base_resource: &str,
) -> Result<()> {
    let access_level = 1; // View is a good default
    let client = db.client().await?;
    let rows = client
        .execute(INSERT_ONE, &[&role, &base_resource, &access_level])
        .await
        .map_err(|_e| Error::InvalidValue)?;
    if rows == 1 {
        Ok(())
    } else {
        Err(Error::InvalidValue)
    }
}

/// Patch permission by name
pub async fn patch_by_name(
    db: &Database,
    name: &str,
    mut obj: Map<String, Value>,
) -> Result<()> {
    let mut client = db.client().await?;
    let transaction = client.transaction().await?;
    let row = transaction
        .query_one(query::PERMISSION_ONE, &[&name])
        .await
        .map_err(|_e| Error::NotFound)?;
    let Permission {
        name,
        mut role,
        mut base_resource,
        mut hashtag,
        mut access_level,
    } = Permission::from_row(row);
    if let Some(Value::String(r)) = obj.remove("role") {
        role = r;
    }
    if let Some(Value::String(r)) = obj.remove("base_resource") {
        base_resource = r;
    }
    match obj.remove("hashtag") {
        Some(Value::String(ht)) => hashtag = Some(ht),
        Some(Value::Null) => hashtag = None,
        _ => (),
    };
    if let Some(Value::Number(a)) = obj.remove("access_level") {
        if let Some(a) = a.as_i64() {
            access_level = a as i32;
        }
    }
    let rows = transaction
        .execute(
            UPDATE_ONE,
            &[&name, &role, &base_resource, &hashtag, &access_level],
        )
        .await
        .map_err(|_e| Error::Conflict)?;
    if rows == 1 {
        transaction.commit().await?;
        Ok(())
    } else {
        Err(Error::Conflict)
    }
}

/// Delete permission by name
pub async fn delete_by_name(db: &Database, name: &str) -> Result<()> {
    let client = db.client().await?;
    let rows = client.execute(DELETE_ONE, &[&name]).await?;
    if rows == 1 {
        Ok(())
    } else {
        Err(Error::NotFound)
    }
}

/// Get user permission for a Sonar name
pub async fn get_by_name(
    db: &Database,
    user: &str,
    name: &Name,
) -> Result<Permission> {
    let client = db.client().await?;
    let base_resource = name.res_type.base().as_str();
    match name.object_n() {
        Some(tag) => {
            let row = client
                .query_one(QUERY_PERMISSION_TAG, &[&user, &base_resource, &tag])
                .await
                .map_err(|_e| Error::Forbidden)?;
            Ok(Permission::from_row(row))
        }
        None => {
            let row = client
                .query_one(QUERY_PERMISSION, &[&user, &base_resource])
                .await
                .map_err(|_e| Error::Forbidden)?;
            Ok(Permission::from_row(row))
        }
    }
}
