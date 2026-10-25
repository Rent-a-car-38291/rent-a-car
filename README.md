# rent-a-car

[![Build](https://github.com/Rent-a-car-38291/rent-a-car/actions/workflows/build.yml/badge.svg)](https://github.com/Rent-a-car-38291/rent-a-car/actions/workflows/build.yml)

A car rental backend for the Avans course: people offer their car (or the cars of their rental company), renters reserve it for a part of the day, drive, and earn bonus points for calm driving. There is no frontend: the API is described in Swagger UI, and a [console client](#console-client) lets you use every feature from menus.

Built with [Ktor](https://ktor.io) and Kotlin, and the [Kotlin Toolchain](https://github.com/JetBrains/amper) (Amper).

## Features

- **Cars**: three kinds, combustion (`ICE`), battery electric (`BEV`) and hydrogen (`FCEV`), with a sample fleet. Offer, update and delete your own car, with photos (JPEG or PNG) and a service record that says when a car is due for service.
- **Finding a car**: filter by type, body style, transmission, fuel, minimum range, maximum rate and distance from a location, sort by rate, range or cost per km, or show only the cars that are free on a date and daypart. Lists are paged.
- **Costs**: the energy cost per km of a car, a quote for a trip of a given distance (energy plus rental rate), the total cost of ownership (TCO) per year, and a comparison of the cost and CO2 of the three car types for a distance.
- **Reservations**: reserve a car for the morning, afternoon or evening of a day (one reservation per car, date and daypart, also when two people try at once), see which dayparts are free, list your own reservations by status (`RESERVED`, `COMPLETED`, `CANCELLED`) and cancel one before it starts.
- **Trips and bonus points**: record a trip in one call, or start it and end it later so the duration is known. The server scores the trip on hard accelerations and hard brakes and awards bonus points. Trip history for a renter and for a car's managers, driving totals per user, and a public leaderboard.
- **Ratings**: rate a car once after a finished trip; the average shows on the car, and the ratings with their count are in the car's ratings list.
- **Accounts**: register, log in and out, update your name and email, change your password and delete your account. Passwords are stored hashed, login tokens are stored hashed and expire after 7 days, and repeated failed logins for one email are refused for a while (`429`).
- **Organizations**: create an organization (a rental company), invite people with a one-time code, give members the role `ADMIN` or `MEMBER`, let every member manage the organization's cars (only an admin deletes one) and see a usage and TCO report per car.
- **API**: one error body for every error, `limit`/`offset` paging with `X-Total-Count`, Swagger UI in development mode and an OpenAPI description.
- **Console client**: menus for all of the above, to demo the application without a frontend.
- Cars, accounts, organizations, reservations, trips, ratings, photo records and tokens are stored in PostgreSQL; the tables are created when the server starts.

## Getting started

You need Docker, for the database. You do not need a JDK: the `./kotlin` wrapper downloads the toolchain and a matching JDK on the first run (this takes a minute or two), so there is nothing else to install. On Windows, use `kotlin.bat` instead of `./kotlin`.

Start the database, then the server:

```bash
docker compose up -d
./kotlin run -m rent-a-car
```

The project has two modules (the server and the [console client](#console-client)), so `run` needs `-m` with the module to start. The server module is named after the folder you cloned into (`rent-a-car`); `./kotlin show modules` lists the names.

If the server starts successfully, you'll see:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

Try it:

```bash
curl http://localhost:8080/cars
```

| Command          | Description       |
|------------------|-------------------|
| `./kotlin run -m rent-a-car` | Run the server |
| `./kotlin run -m cli` | Run the console client |
| `./kotlin test`  | Run the tests of both modules |
| `./kotlin build` | Build both modules |

### Tests

`./kotlin test` runs everything; a single class runs with `./kotlin test --include-classes=com.example.cars.CarFilterTest`.

- **Unit tests** check one piece of logic and need no server or database: `CarFilterTest`, `AddCarRequestTest`, `CostPerKmTest`, `QuoteTest`, `CompareTest`, `TcoTest`, `TripScoreTest`, `TripDurationTest`, `DaypartTest`, `FreeSlotTest`. They run in milliseconds.
- **Integration tests** (the route and repository tests) start the application with `testApplication` and a real Postgres that the tests start themselves, and check status codes and what ends up in the database. `CarAccessTest` is a unit test of the access rules that uses that Postgres for the memberships.
- **Console client tests** (`cli/test`): the menus with scripted input and a fake API, the error messages, and `EndToEndTest`, which starts the real server on a random port with its own Postgres and drives register, list cars, reserve, trip and leaderboard through the real HTTP client.

A rule gets a unit test for each edge value; a route gets an integration test for each status code it can answer.

The connection defaults match `docker-compose.yml`. Override them with the environment variables `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. Car photos are stored as files in the directory `uploads` (set `IMAGES_DIRECTORY` to change it), next to the database rows that hold their urls. The tables are created when the server starts, and the cars table is filled with sample cars when it is empty. Login tokens are stored (only their SHA-256 hash) and expire 7 days after login, so a restart does not log anyone out. The tests don't need Docker: they start their own Postgres.

## Configuration

The defaults match `docker-compose.yml` and need no setup for local development.

| Variable | Default | Used for |
| -------- | ------- | -------- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/rentacar` | JDBC url of the PostgreSQL database |
| `DATABASE_USER` | `rentacar` | Database user |
| `DATABASE_PASSWORD` | `rentacar` | Database password |
| `IMAGES_DIRECTORY` | `uploads` | Directory where the car photos are stored |
| `KTOR_DEVELOPMENT` | `true` | Development mode, which serves Swagger UI at `/swagger`; set `false` outside local development |
| `RENT_A_CAR_URL` | `http://localhost:8080` | Server the console client talks to (or pass the url as its first argument) |

## Console client

`cli/` is a small client that talks to the running server over HTTP, so the whole application can be demoed without a frontend. It is a separate Amper module next to the server (the server is the root module, `project.yaml` adds the client): that keeps the client's code out of the server, needs no new dependency (it uses the JDK's HTTP client and kotlinx.serialization, which the project already has), and lets `./kotlin test` and `./kotlin build` cover both. The cost is that `./kotlin run` now needs `-m <module>`.

```bash
./kotlin run -m cli                                  # talks to http://localhost:8080
RENT_A_CAR_URL=http://localhost:9000 ./kotlin run -m cli   # another server (or pass the url as the first argument)
```

Start screen: register, log in, browse as a visitor (the car list, availability, costs and the leaderboard need no login) or quit. After login the main menu has the areas **Cars** (search, details with photos and ratings, availability, offer, update and delete your car, mark it as serviced, add a photo), **Costs** (cost per km, quote, compare car types, TCO), **Reservations** (reserve, list yours with their status, cancel), **Trips** (start, end, record in one go, history, the trips of a car you manage, rate), **Insights** (leaderboard, your stats), **Account** (update, password, delete) and **Organizations** (create, invite, join, roles, leave, organization cars, fleet report). Type `back` at any prompt to cancel the action; `0` goes one menu up. Cars are picked from a numbered list, dates are `YYYY-MM-DD` and dayparts are picked from a list. Long lists (cars, reservations, trips, the leaderboard) are shown page by page: the client says how many of the total it showed and asks for the next page. A failed call shows the message of the server's error body.

The token is only kept in memory and the passwords are echoed while typing (`shortcut:` plain stdin). The API has no "who am I", no list of your organizations and no list of members, so the client shows the account id when you register (it asks for it once if you only log in) and keeps the reservations and organizations you made during the run for its pickers; others can be typed by id (**Reservations > My reservations** shows the ids). Listing members is not in the client (the API has no such endpoint).

### Adding a menu entry

Each area is one file in `cli/src/areas/` that defines a `Menu` of `MenuItem`s; `cli/src/Areas.kt` lists the areas.

1. New endpoint in an existing area: add a `MenuItem("Label") { ctx -> ... }` to that file's list. Inside, prompt with `ctx.io` (`text`, `int`, `double`, `date`, `choose`, `optionalText` ...), call the API with `ctx.call("GET", "/path", query, body, hints)` and print the answer with `ctx.io.table(...)` or `ctx.io.show(...)`. `ctx.call` already shows a friendly message for `400`, `401`, `403`, `404`, `409`, `413`, `429` and server errors (`hints` replace the text of a code for this action) and returns `null` then. Pickers: `ctx.pickCar()`, `ctx.pickReservation()`, `ctx.pickOrganization()`.
2. New area: copy a file from `areas/`, name the `Menu`, and add it to the list in `Areas.kt`.
3. `MenuItem(needsLogin = false)` keeps an item visible to visitors; the default hides it until login.

```kotlin
MenuItem("Cost per km of a car", needsLogin = false) { ctx ->
    val id = ctx.pickCar() ?: return@MenuItem
    ctx.call("GET", "/cars/$id/cost-per-km")?.let { ctx.io.show(it.jsonObject) }
}
```

Menus only use the `Api` interface and the injected `Console`, so tests drive them with scripted input and a fake API (`cli/test/FakeApi.kt`); `EndToEndTest` runs the client against the real server and a real Postgres.

### Demo walkthrough

About two minutes, with two terminals:

```bash
docker compose up -d
./kotlin run -m rent-a-car      # terminal 1: the server
./kotlin run -m cli             # terminal 2: the client
```

1. **Register** (name, email, a password of 8+ characters). You are logged in and the main menu opens.
2. **Cars > List and search cars**: press Enter on every filter to see the fleet; try type `BEV`, or sort by `costPerKm` to see the cheapest car per km first. **Car details** shows photos and ratings. **Offer my car** adds a car of your own, and **Add a photo to a car I manage** uploads a JPEG or PNG from a path on your computer (5 MB at most, 10 photos per car); the photo shows in the car details.
3. **Costs > Quote a trip**: pick a car and a distance. **Cost per km** shows the energy price.
4. **Reservations > Reserve a car**: pick a car, press Enter for today and pick a daypart. **My reservations** shows them with their status.
5. **Trips > Start my trip**, then **End my trip** (distance, hard accelerations, hard brakes): you earn bonus points. **Rate the car** afterwards.
6. **Insights > Leaderboard** and **My driving stats** show your points.
7. **Organizations > Create an organization**, then **Invite someone** for a code; a second client (register another account) can **Join with an invite code**.
8. `0` to **Log out**, `0` to **Quit**.

## Trying the API (Swagger UI)

With the server running, open [http://localhost:8080/swagger](http://localhost:8080/swagger) to see every endpoint and try it with "Try it out". The page is described by [`resources/openapi/documentation.yaml`](resources/openapi/documentation.yaml), so add new endpoints there as well.

Swagger UI is only served in Ktor's development mode, which is on by default. Start the server with `KTOR_DEVELOPMENT=false` to turn it off.

## HTTP demo (IntelliJ)

[`http/`](http) has request files for the [IntelliJ HTTP Client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) that walk through the REST API as user flows, one click per request. Every request checks its status code (and a key field where that is cheap), so a run shows green or red per request, and the whole file doubles as a repeatable test of the running server.

| File | What it does |
|------|--------------|
| `demo.http` | The main demo, in five blocks: **Blok 1** a visitor (cars, filters, sorting, nearest, costs, quote, compare, leaderboard), **Blok 2** an account and a car (Anna: register, log in, offer a car, duplicate plate, TCO, service, photo), **Blok 3** renting and driving (Ben: reserve, double booking, cancel, start and end a trip, rating, stats), **Blok 4** an organization (invite, organization car, report as JSON and CSV, member and admin rules), **Blok 5** errors and security (401, 403, 404, 400 and the 429 after five wrong passwords) |
| `errors.http` | More error cases next to the demo: validation, someone else's data, a date in the past, a trip too early, a rating without a trip, a file that is not a picture, an unknown route. It sets up its own users |
| `http-client.env.json` | The environment `dev`: `baseUrl` is `http://localhost:8080` |
| `tiny.png` | A 1x1 picture for the photo upload |

Start the server (see [Getting started](#getting-started)), open `http/demo.http` in IntelliJ IDEA, choose the environment `dev` in the top right of the editor and use **Run all requests in file**, or run one request with the green arrow. The blocks build on each other: the token, the ids and the invite code are passed on with `client.global.set`, so nothing has to be copied. The file can run again on the same database, because every run uses a new run id for the emails and the license plates and offers a new car. It works at any time of day: "today" is calculated in Dutch time, like the server does.

Without IntelliJ, the same files run with the [HTTP Client CLI](https://www.jetbrains.com/help/idea/http-client-cli.html) from Docker (`-D` makes `localhost` reach the server on your machine):

```bash
docker run --rm -v "$PWD/http:/workdir" jetbrains/intellij-http-client -D -v http-client.env.json -e dev --no-progress demo.http
```

For another server, add `-V baseUrl=http://localhost:9090` (the address as the server runs on your machine).

## API

Every endpoint, taken from [`documentation.yaml`](resources/openapi/documentation.yaml). `Login` says whether the `Authorization: Bearer <token>` header is needed; every endpoint can also answer `500` and the other codes of [Errors and paging](#errors-and-paging). The paragraphs below the table explain the rules per area.

| Method   | Path                                   | Description                                             | Login | Success | Errors                                   |
| -------- | -------------------------------------- | ------------------------------------------------------- | ----- | ------- | ---------------------------------------- |
| `GET`    | `/cars`                                | List cars                                               | no    | `200`   | `400`                                    |
| `POST`   | `/cars`                                | Offer a car                                             | yes   | `201`   | `400`, `401`, `403`, `409`               |
| `GET`    | `/cars/compare`                        | Compare car types for a distance                        | no    | `200`   | `400`                                    |
| `GET`    | `/cars/{id}/cost-per-km`               | Running cost per km                                     | no    | `200`   | `400`, `404`                             |
| `GET`    | `/cars/{id}/quote`                     | Quote a trip before reserving                           | no    | `200`   | `400`, `404`                             |
| `GET`    | `/cars/{id}/tco`                       | Total cost of ownership per year                        | yes   | `200`   | `400`, `401`, `403`, `404`               |
| `POST`   | `/cars/{id}/images`                    | Add a photo to a car                                    | yes   | `201`   | `400`, `401`, `403`, `404`, `409`, `413` |
| `GET`    | `/images/{name}`                       | Download a photo                                        | no    | `200`   | `404`                                    |
| `POST`   | `/cars/{id}/reservations`              | Reserve a car for a daypart                             | yes   | `201`   | `400`, `401`, `404`, `409`               |
| `GET`    | `/reservations`                        | Your reservations                                       | yes   | `200`   | `400`, `401`                             |
| `DELETE` | `/reservations/{id}`                   | Cancel your reservation                                 | yes   | `204`   | `400`, `401`, `403`, `404`, `409`        |
| `POST`   | `/reservations/{id}/trip`              | Record the trip of a reservation and award bonus points | yes   | `201`   | `400`, `401`, `403`, `404`, `409`        |
| `POST`   | `/reservations/{id}/trip/start`        | Start the trip of a reservation                         | yes   | `200`   | `400`, `401`, `403`, `404`, `409`        |
| `POST`   | `/reservations/{id}/trip/end`          | End a started trip and award bonus points               | yes   | `201`   | `400`, `401`, `403`, `404`, `409`        |
| `GET`    | `/trips`                               | Your trip history                                       | yes   | `200`   | `400`, `401`                             |
| `GET`    | `/cars/{id}/trips`                     | The trips of a car                                      | yes   | `200`   | `400`, `401`, `403`, `404`               |
| `GET`    | `/accounts/{id}/stats`                 | Your driving totals                                     | yes   | `200`   | `400`, `401`, `403`, `404`               |
| `GET`    | `/leaderboard`                         | The drivers with the most bonus points                  | no    | `200`   | `400`                                    |
| `POST`   | `/reservations/{id}/rating`            | Rate the car of your reservation                        | yes   | `201`   | `400`, `401`, `403`, `404`, `409`        |
| `GET`    | `/cars/{id}/ratings`                   | The ratings of a car                                    | no    | `200`   | `400`, `404`                             |
| `GET`    | `/cars/{id}/availability`              | Free dayparts of a car on a date                        | no    | `200`   | `400`, `404`                             |
| `PUT`    | `/cars/{id}/service`                   | Mark a car as serviced                                  | yes   | `204`   | `400`, `401`, `403`, `404`               |
| `GET`    | `/cars/{id}`                           | Get a car                                               | yes   | `200`   | `400`, `401`, `404`                      |
| `PUT`    | `/cars/{id}`                           | Update your own car, or an organization car as a member | yes   | `200`   | `400`, `401`, `403`, `404`, `409`        |
| `DELETE` | `/cars/{id}`                           | Delete your own car, or an organization car as an admin | yes   | `204`   | `400`, `401`, `403`, `404`               |
| `POST`   | `/accounts`                            | Register an account                                     | no    | `201`   | `400`, `409`                             |
| `POST`   | `/login`                               | Log in and get a bearer token                           | no    | `200`   | `400`, `401`, `429`                      |
| `POST`   | `/logout`                              | Log out                                                 | yes   | `204`   | `401`                                    |
| `PUT`    | `/accounts/{id}`                       | Update your own name and email                          | yes   | `200`   | `400`, `401`, `403`, `404`, `409`        |
| `DELETE` | `/accounts/{id}`                       | Delete your own account                                 | yes   | `204`   | `400`, `401`, `403`, `404`, `409`        |
| `PUT`    | `/accounts/{id}/password`              | Change your own password                                | yes   | `204`   | `400`, `401`, `403`, `404`               |
| `POST`   | `/organizations`                       | Create an organization                                  | yes   | `201`   | `400`, `401`                             |
| `POST`   | `/organizations/{id}/invites`          | Create an invite code (admin only)                      | yes   | `201`   | `400`, `401`, `403`, `404`               |
| `GET`    | `/organizations/{id}/report`           | Usage and TCO per car of the organization               | yes   | `200`   | `400`, `401`, `403`, `404`               |
| `POST`   | `/invites/{code}/accept`               | Join an organization with an invite code                | yes   | `204`   | `401`, `404`, `409`                      |
| `PUT`    | `/organizations/{id}/members/{userId}` | Change the role of a member (admin only)                | yes   | `200`   | `400`, `401`, `403`, `404`, `409`        |
| `DELETE` | `/organizations/{id}/members/{userId}` | Remove a member or leave                                | yes   | `204`   | `400`, `401`, `403`, `404`, `409`        |

Cars are found with the query parameters of `GET /cars`, which all combine: `type`, `maxRate`, `bodyStyle`, `transmission`, `fuelType` (only combustion cars have one), `minRange` (km on a full tank or battery) and a location (`lat`, `lon`, `maxDistanceKm`). `sort` orders by `rate`, `range` or `costPerKm` (energy cost per km at the standard prices), with a leading `-` for descending, for example `GET /cars?sort=costPerKm` lists the cheapest car per km first; equal values keep the fleet order. With a location the nearest car always comes first and `sort` is only checked. An unknown filter or sort value gives `400`.

A car is rented per daypart: `MORNING` (06:00-12:00), `AFTERNOON` (12:00-18:00) or `EVENING` (18:00-24:00), once per car and date. Pass the `date` (`2026-10-12`) and `daypart` to `POST /cars/{id}/reservations`; `GET /cars/{id}/availability?date=2026-10-12` lists what is still free. `GET /cars?date=2026-10-12&daypart=MORNING` lists only the cars that are free then (not reserved, not in `Maintenance`) and combines with the other filters; `date` and `daypart` come together, and a missing, invalid or past value gives `400`. A car in `Maintenance` can't be reserved. There is no `Rented` status. A reservation has a `status`: `RESERVED` (upcoming), `COMPLETED` (its trip was recorded) or `CANCELLED`. The renter can cancel with `DELETE /reservations/{id}` until the daypart starts (its `from` time): the reservation stays in the list as `CANCELLED` and its daypart is free again for anyone (only one active reservation per car, date and daypart). After the daypart starts, with a trip or a started trip, or when it is already completed or cancelled, the answer is `409`; a trip can't be recorded or started on a cancelled reservation (`409`). `GET /reservations` lists your own reservations, newest first, with an optional `status` filter and `limit` (1-100, default 20) and `offset` paging; an invalid value gives `400`.

A car is private or belongs to an organization. `POST /cars` with an `organizationId` (you must be a member, else `403`) makes an organization car: it has no `ownerId`, every member can update it and see its TCO, and only an admin can delete it. Private cars are managed by their owner only. Deleting an account removes its private cars; organization cars stay.

Service: a car has a `mileage` (km) and `lastServiceKm`, the mileage at its last service. A new car counts as serviced at its mileage (or at the optional `lastServiceKm` you give, 0 up to the mileage). Every recorded trip adds its `distanceKm` to the mileage. `GET /cars/{id}` shows `serviceDue`: true when more than 15000 km have passed since the last service. `PUT /cars/{id}/service` (owner, or a member for an organization car) sets the last service to the current mileage. A mileage can be at most 10 000 000 km.

Photos: `POST /cars/{id}/images` takes a multipart form with the photo in the part `file` (JPEG or PNG, up to 5 MB and 10 per car, `409` beyond that; whoever may manage the car). The server checks the file's first bytes instead of trusting its name or declared type, stores it under a random name and answers `{id, carId, url}`. `GET /cars/{id}` lists the urls in `images`, and every url serves the photo without a login. Deleting a car removes its photos too.

After driving, the renter sends the trip of their reservation to `POST /reservations/{id}/trip` with `distanceKm`, `hardAccelerations` and `hardBrakes` as the app measured them, once per reservation and not before its date. The server calculates the `score` (100, minus 5 for every hard acceleration or brake per 100 km, never below 0) and awards `distanceKm * score / 100` points (rounded down) to the renter's `bonusPoints`, which accounts show.

The app can also time the trip: `POST /reservations/{id}/trip/start` (on the day of the reservation, once) stores the start, and `POST /reservations/{id}/trip/end` with the same body as above ends it. The trip then has `startedAt` and `durationMinutes` (whole minutes, rounded up, at least 1), and the points are awarded as before. Ending a trip that was not started, or twice, gives `409`, and so does sending a started trip in one call; a trip sent in one call has both fields `null`.

`GET /trips` lists your own trips, newest first, with the car, date, daypart, `distanceKm` and `score`. `from` and `to` (ISO dates, inclusive) filter on the date of the reservation and `limit` (1-100, default 20) and `offset` page through the list; an invalid value gives `400`. `GET /cars/{id}/trips` does the same for everyone's trips with a car, for the owner of a private car or a member of the organization of an organization car (`403` for others).

`GET /accounts/{id}/stats` shows your driving totals: `tripCount`, `totalKm`, `averageScore` (one decimal), `bestDaypart` (the daypart with the highest average score, the earliest on a tie) and `bonusPoints`. `averageScore` and `bestDaypart` are `null` without trips. Only finished trips count, and it only works on your own account (`403` for someone else's, `404` for an unknown one).

`GET /organizations/{id}/report` is for every member of the organization (they may all manage its cars) and has one row per organization car, in car order: `carId`, `brand`, `licensePlate`, `reservations` (not cancelled), `trips` and `totalKm` (finished trips), `averageScore` (one decimal, `null` without trips) and `tcoPerYear` (TCO at 15,000 km a year, in EUR). `?format=csv` returns the same rows as CSV with a header line (cells that start like a spreadsheet formula get a leading `'`); any other `format` gives `400`, a non-member `403` and an unknown organization `404`.

`GET /leaderboard` is public and lists the drivers with the most bonus points, each with only a `name` and `bonusPoints` (never the email). `limit` (1-100, default 10) cuts the list and anything else gives `400`. Equal points are ordered by name and then by account, and users without points are not listed.

After a finished trip the renter can rate the car once with `POST /reservations/{id}/rating`: `stars` (1 to 5) and an optional `comment` (up to 500 characters, trimmed). `409` without a finished trip (a started trip that has not ended does not count) or when the reservation was already rated. `GET /cars/{id}/ratings` lists the ratings of a car, newest first, with `averageRating` (one decimal, `null` without ratings) and `ratingCount`; a rating shows no data of the renter. `GET /cars` and `GET /cars/{id}` also show the car's `averageRating`. Deleting a car or an account removes the ratings that belong to it.

A password needs 8 to 128 characters, and the email address must be unused. Everything under `/accounts/{id}` and `/logout` needs a login and only works on your own account. Routes that need a login take the token from `POST /login` in an `Authorization: Bearer <token>` header; a token is valid for 7 days and then answers `401` like an unknown one. After 5 failed logins for one email within 15 minutes, `POST /login` answers `429` with a `Retry-After` header (seconds), also for the correct password; a successful login resets the count, and an email that has no account is counted too. The counters live in memory, so a restart resets them. Each endpoint has a [sequence diagram](docs/diagrams) with every status code.

```bash
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
     -d '{"name":"Sam","email":"sam@example.com","password":"correct horse"}'

curl -X POST localhost:8080/login -H 'Content-Type: application/json' \
     -d '{"email":"sam@example.com","password":"correct horse"}'
# {"token":"..."}
```

## Errors and paging

Every error response (`400`, `401`, `403`, `404`, `405`, `409`, `413`, `415`, `429`, `500`) has the same body, also the errors that Ktor answers itself, such as an unknown route or an unreadable body:

```json
{ "error": "not_found", "message": "Car with ID 5 not found" }
```

`error` is a code that does not change (the status in lower case with underscores), `message` is a text for people. A `401` carries `WWW-Authenticate: Bearer` and a `429` keeps its `Retry-After` header. An unexpected failure is a `500` that never shows the details; they are only in the server log. Only a `406` (the client does not accept JSON) cannot have this body. See [`sequence-errors`](docs/diagrams/sequence-errors.png).

Lists are paged with `limit` (1 to 100, default 20) and `offset` (default 0), and `400` for any other value. The body is the page, a plain list; the size of the whole list is in the `X-Total-Count` header, so a client can page through everything:

```bash
curl -i 'localhost:8080/cars?limit=5&offset=10'
# X-Total-Count: 13
```

This holds for `GET /cars` (after the filters), `GET /reservations`, `GET /trips`, `GET /cars/{id}/trips` and `GET /leaderboard`, which shows 10 drivers by default.

To page in code, parse the parameters with `parsePaging` (`src/Paging.kt`), set the header with `call.setTotalCount(total)` and answer with the page. A route answers an error with `call.respond(HttpStatusCode.NotFound)` or, for a message about this request, `call.respondError(HttpStatusCode.NotFound, "...")` (`src/Errors.kt`): never build the error body by hand.

## Project layout

```
src/
├── Application.kt        wires the modules together
├── Errors.kt, Paging.kt  the one error body and the paging helper every route uses
├── Routing.kt            static files and Swagger UI
├── Serialization.kt      JSON
├── accounts/             users, login tokens, login rate limit, account routes
├── cars/                 the car hierarchy, repository, filters, costs, quote, compare and routes
├── database/             the PostgreSQL connection
├── images/               car photos: records, files on disk and routes
├── organizations/        organizations, invites, members, the usage report
├── ratings/              ratings of a car after a trip
├── reservations/         reservations by daypart, their status and availability
└── trips/                trips, scoring and bonus points, history, stats, leaderboard
resources/                application.yaml (settings), logback.xml, openapi/documentation.yaml and static/
test/                     mirrors src/ (unit and route tests, test Postgres in database/)
cli/                      the console client, its own module (src/areas/ has one file per menu)
http/                     IntelliJ HTTP Client files: demo.http, errors.http, the environment and a test picture
docs/diagrams/            PlantUML sources and exported PNGs
.github/workflows/        the build: ./kotlin test and ./kotlin build on every push and pull request
module.yaml, project.yaml the Amper build files: the server module and the list of modules
docker-compose.yml        the local PostgreSQL
```

## Diagrams

Diagrams live in [`docs/diagrams`](docs/diagrams) as `.puml` files, each with an exported `.png` next to it so GitHub can show it without a plugin.

| Diagram                 | Shows                                                                         |
|-------------------------|-------------------------------------------------------------------------------|
| `use-cases`             | What each kind of user can do, taken from the issues                          |
| `domain-model`          | The domain as planned in the backlog, with fields and methods                 |
| `class-<area>`          | The classes as they are in `src/`, one diagram per area (`cars`, `accounts`)  |
| `components`            | How a request flows through plugins, routes and repositories                  |
| `sequence-<endpoint>`   | One flow per endpoint, with every status code                                 |

A PR that changes an endpoint or a class updates its diagram and PNG, and shows them in the PR description.

![Components](docs/diagrams/components.png)

### Viewing and editing

1. **Install the plugin.** In IntelliJ IDEA, go to `Settings/Preferences > Plugins > Marketplace`, search for **PlantUML Integration** and install it.
2. **Install Graphviz**, which PlantUML renders with:
   - macOS: `brew install graphviz`
   - Windows: `choco install graphviz`, or the installer from the [Graphviz website](https://graphviz.org/download/) with "Add Graphviz to the system PATH" checked.
3. Restart your IDE and open a `.puml` file to see the preview.

### Exporting images

After changing a `.puml`, export the PNGs again with the PlantUML command line (`brew install plantuml`) and commit them with it:

```bash
plantuml -tpng docs/diagrams/*.puml
```
