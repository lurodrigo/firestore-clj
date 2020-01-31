# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.3.0 - 2020-01-31
### Modified
* Lots of changes on internals, finer control of results' representation as data.

## 0.2.1 - 2020-01-30
### Modified
* Moved `org.clojure/clojure` to dev dependencies.

## 0.2.0 - 2020-01-30
### Added 
* Support for transactions and batched writes
* `doc` with 1-arity

### Modified
* `set!` now takes a DocumentReference
* Renamed `collection` -> `coll`, `document` -> `doc`, `field-delete` -> `mark-for-deletion`.

## 0.1.3 - 2020-01-29
### Added
* All functions now have proper type hints to avoid reflection.

### Modified
* Renamed `in`, `contains`, `contains` any, adding a `filter-` prefix.

## 0.1.2 - 2020-01-29
### Added
* Converts `com.google.cloud.Timestamp` to `java.util.Date` when receiving data.

### Modified
- Renamed `field-delete` -> `delete`.

## 0.1.1 - 2020-01-29
### Added
* `merge!`, `assoc!`, `dissoc!`, `field-delete`, `inc`, `server-timestamp`, `array-union`, `array-remove`, `id`

### Modified
- `->atom` now returns an atom instead of a promise.
- Renamed `add` -> `add!`, `set` -> `set!`, `delete` -> `delete!`

## 0.1.0 - 2020-01-28
### Added
- First release