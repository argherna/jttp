# Roadmap

## Issues

* Try to find a way to support HTTP/2 while still using HttpURLConnection.
* Support PATCH.
* Address rendering issues.

## Features

### 1.1

* Using the `history.xml` file along with the other files in the zipped session file, specify options to list, update, and playback entries in that file.

### 1.2

* Support OAuth without the need for an additional script.
   * With 1.0, the best way to handle OAuth is to first obtain a code from your OAuth provider, then write a script that generates the refresh token and bearer.
