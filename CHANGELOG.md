# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

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