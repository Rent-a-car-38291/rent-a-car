# Frontend page design

Design for the rent-a-car frontend: which pages exist, what each one shows, what text it uses and what a person can do there. This is the source for the GitHub issues and the wireframes. Nothing here is built yet.

## Decisions

| Decision | Choice |
|---|---|
| Platforms | iPhone, Android and a web portal, from one shared Compose Multiplatform UI |
| Wireframes | Two per page: mobile and desktop |
| Driving a trip | Phones only, and only with location turned on. The web portal shows trips, it cannot record one |
| Route | A trip records the driven route and shows it on a map |
| Typing trip numbers by hand | Not in the UI (the API allows it, but it lets people invent their own score). See open questions |
| Language of the UI text | English (see open questions) |
| Missing API lists | Become backend issues, listed at the end |
| Style | Acid wave, with our own components (not the stock Material look). See Look and feel |
| Light and dark | Both, chosen by the person: System, Light or Dark |

## Look and feel

Acid wave: one loud acid lime, black, and a wavy line as the motif that comes back everywhere (the route on the map, the score, section dividers).

| | Dark mode ("full acid") | Light mode ("acid light") |
|---|---|---|
| Base | black | warm off-white |
| Accent | acid lime | acid lime, used as a background |
| Side colors | pink and violet | used sparingly |
| Main button | lime with black text | black with lime text |
| Waves | bold, in photos and headers | one wave per screen |

- **The lime rule**: lime is only ever text on black, or a background behind black text. Never lime text on a light background (unreadable).
- **Type**: tight lowercase headlines in a bold display font, small labels in a monospace font. Candidates to try: Space Grotesk, Unbounded or Syne for headlines; Space Mono or JetBrains Mono for labels.
- **Icons**: one family only, Phosphor or Tabler.
- **Own components**: button, card, tag, tab bar, text field and the tap effect are ours. Material stays underneath for the date picker, dialogs and sheets, restyled.
- **Trip in progress** uses only base, lime and huge numbers in both modes. No waves, no side colors.
- **Mode**: follows the device by default. The person can override it in Account settings, and the choice is remembered on the device.

## Navigation

Five destinations. On mobile a bottom bar, on desktop a sidebar on the left.

| Tab | Visitor | Logged in |
|---|---|---|
| Find | yes | yes |
| Bookings | hidden | yes |
| My cars | hidden | yes |
| Insights | leaderboard only | yes |
| Account | shows "Log in" | yes |

A visitor who taps an action that needs an account (Reserve, Rate, Offer a car) goes to Log in and returns to the same page afterwards.

## Rules for every page

- **Loading**: a skeleton of the page, not a spinner on an empty screen.
- **Empty**: one sentence saying what is missing and one button with the way forward. Each page lists its own text.
- **Error**: the server sends one error body `{error, message}` for everything. Show `message` in a banner at the top of the page with a "Try again" button. No connection: "Can't reach the server. Check your connection and try again."
- **401 on any page**: the login expired (tokens last 7 days). Go to Log in with "Your session expired. Log in again."
- **Lists** are paged (`limit`, `offset`, total in `X-Total-Count`). Mobile: load more on scroll. Desktop: "Showing 20 of 134" with next and previous.
- **Money** is in EUR with two decimals. **Dates** are shown as "Wed 11 Nov 2026". **Dayparts** are shown with their hours: Morning (06:00 to 12:00), Afternoon (12:00 to 18:00), Evening (18:00 to 24:00).
- **Destructive actions** (delete, cancel, leave) ask for confirmation in a dialog that names the thing.
- **Desktop** uses the extra width for a second column (list plus detail), never for wider text lines.

---

## Getting in

### 1. Welcome
- **Who**: visitor, first open only.
- **Shows**: app name, one line ("Rent a car for a morning, an afternoon or an evening."), a photo.
- **Actions**: "Create account", "Log in", "Look around first" (goes to Car search).
- **Desktop**: the web portal skips this page and opens Car search with "Log in" in the sidebar.

### 2. Register
- **Fields**: Name, Email, Password (8 to 128 characters, with a show/hide toggle).
- **Button**: "Create account". After success the person is logged in and lands on Car search.
- **Errors**: "Enter your name.", "Enter a valid email address.", "Use at least 8 characters.", "There is already an account with this email." (409).
- **Link**: "Already have an account? Log in".
- **API**: `POST /accounts`, then `POST /login`.

### 3. Log in
- **Fields**: Email, Password.
- **Button**: "Log in".
- **Errors**: "Wrong email or password." (401). "Too many attempts. Try again in 4 minutes." (429, the wait comes from the `Retry-After` header; the button stays disabled until then).
- **Link**: "No account yet? Create one".
- **API**: `POST /login`. The token is kept in the device's secure storage.

---

## Find

### 4. Car search
- **Who**: everyone.
- **Shows per car**: photo, brand, type badge (Electric, Combustion, Hydrogen), body style, rate per daypart, range, average rating with stars, distance from you (when location is known).
- **Free on**: a date and a daypart at the top. When set, only cars that are free then are shown. Dates in the past are refused.
- **Filters**: type, body style (Hatchback, SUV, Sedan, Stationwagon, Cabrio, Coupe, MPV, Other), transmission (Manual, Automatic), fuel (Petrol, Diesel, LPG, only for combustion), minimum range, maximum rate, maximum distance from me.
- **Sort**: rate, range, cost per km, each ascending or descending.
- **Actions**: open a car, change filters, "Clear filters", "Compare car types".
- **Empty**: "No cars match these filters." with "Clear filters".
- **Mobile**: a list of cards. Filters open as a sheet from a "Filters (3)" button.
- **Desktop**: filters always visible in a left column, cards in a grid.
- **API**: `GET /cars` with `type`, `bodyStyle`, `transmission`, `fuelType`, `minRange`, `maxRate`, `lat`, `lon`, `maxDistanceKm`, `sort`, `date`, `daypart`, `limit`, `offset`.

### 5. Car detail
- **Who**: everyone.
- **Shows**: photo gallery (up to 10), brand, license plate, type, body style, doors, model year, power, transmission, color, mileage, rate, where the car is (a small map), average rating and count, cost per km, and a "Service due" notice when it is.
- **Type specific**: combustion shows fuel type, tank size and consumption; electric shows battery size and consumption; hydrogen shows tank size and consumption.
- **Actions**: "Reserve" (main button), "Calculate a trip", "See ratings". If this is your car or your organization's car: "Manage".
- **Status Maintenance**: the Reserve button is replaced by "This car is in maintenance."
- **Mobile**: gallery on top, the Reserve button fixed at the bottom.
- **Desktop**: gallery left, facts and Reserve right.
- **API**: `GET /cars/{id}`, `GET /cars/{id}/cost-per-km`, `GET /images/{name}`.

### 6. Car ratings
- **Shows**: average and count at the top ("4.3 from 12 ratings"), then each rating with stars and comment.
- **Empty**: "No ratings yet."
- **Mobile**: its own page. **Desktop**: a section inside Car detail.
- **API**: `GET /cars/{id}/ratings`.

### 7. Trip quote
- **Field**: Distance in km (1 to 10,000).
- **Shows**: energy cost, rental rate, total.
- **Text**: "A trip of 120 km in this car costs about €48.20."
- **Error**: "Enter a distance between 1 and 10,000 km."
- **Mobile**: a sheet over Car detail. **Desktop**: a panel inside Car detail.
- **API**: `GET /cars/{id}/quote?distanceKm=`.

### 8. Compare car types
- **Field**: Distance in km.
- **Shows**: one column per type (Electric, Combustion, Hydrogen) with the number of cars, the average cost and the CO2 in kg. The cheapest and the cleanest are marked.
- **Mobile**: three stacked cards. **Desktop**: three columns with a bar chart for cost and one for CO2.
- **API**: `GET /cars/compare?distanceKm=`.

### 9. Reserve
- **Who**: logged in.
- **Shows**: the car (photo, brand, rate), a date picker, and the three dayparts for that date. Taken dayparts are greyed out with "Taken".
- **Button**: "Reserve for €35.00".
- **After success**: "Reserved. Wed 11 Nov, Morning." with "See my bookings".
- **Errors**: "Someone just took this slot. Pick another." (409, the dayparts refresh). "Pick a date from today on."
- **Empty**: "This car is fully booked on this day."
- **Mobile**: a full page. **Desktop**: a dialog over Car detail.
- **API**: `GET /cars/{id}/availability?date=`, `POST /cars/{id}/reservations`.

---

## Bookings

### 10. My reservations
- **Shows**: three tabs, Upcoming (RESERVED), Done (COMPLETED), Cancelled. Per row: car photo and brand, date, daypart, status.
- **Actions**: open a reservation.
- **Empty**: Upcoming: "Nothing booked yet." with "Find a car". Done: "No trips driven yet." Cancelled: "Nothing cancelled."
- **Desktop**: list left, the chosen reservation's detail right.
- **API**: `GET /reservations?status=`. Needs backend: the car's brand and photo in each row (today only `carId`).

### 11. Reservation detail
- **Shows**: the car, date, daypart, status, and what comes next.
- **Actions by state**:
  - Upcoming, not started: "Cancel reservation".
  - Upcoming, the daypart is now, on a phone: "Start trip". On the web portal: "Open the app on your phone to drive this trip."
  - Trip running: "Go to trip".
  - Done, not rated: "Rate this car". Done, rated: your stars and comment.
- **Confirm cancel**: "Cancel your reservation for Wed 11 Nov, Morning?" with "Cancel reservation" and "Keep it".
- **Errors**: "This reservation has already started and can't be cancelled."
- **API**: `DELETE /reservations/{id}`.

### 12. Trip in progress (phones only)
- **Before starting**: asks for location permission with the reason: "We use your location during this trip to measure the distance, draw your route and score how calmly you drive. Tracking stops when you end the trip."
- **Location off or refused**: "Turn on location to drive. A trip can't be recorded without it." with "Open settings". The trip cannot start.
- **Shows while driving**: a map with the route so far, time driven, distance, the number of hard accelerations and hard brakes. Large text, few controls, readable at a glance.
- **Button**: "End trip", with confirmation "End this trip?".
- **If the app is closed mid trip**: reopening returns to this page and tracking continues.
- **API**: `POST /reservations/{id}/trip/start`, `POST /reservations/{id}/trip/end` with distance, hard accelerations and hard brakes. Needs backend: the route points.

### 13. Trip result
- **Shows**: the score out of 100 (large), points earned and new total ("+84 points. You now have 1,240."), distance, duration, hard accelerations, hard brakes, and the route on a map.
- **Explains the score**: "100 for a calm drive, 5 less for every hard acceleration or brake per 100 km."
- **Actions**: "Rate this car", "Done".
- **Mobile**: score on top, map below. **Desktop**: map left, numbers right. Reached from Trip history too.
- **API**: the answer of `trip/end`. Needs backend: `GET /trips/{id}` with the route, so an old trip can be opened again.

### 14. Rate car
- **Fields**: 1 to 5 stars (required), Comment (optional, up to 500 characters with a counter).
- **Button**: "Send rating".
- **Errors**: "You already rated this trip." (409). "You can rate a car after you finished the trip."
- **Mobile**: a sheet. **Desktop**: a dialog.
- **API**: `POST /reservations/{id}/rating`.

### 15. Trip history
- **Shows**: per trip the date, daypart, car, distance, score. A date range filter (from, to).
- **Actions**: open a trip (goes to Trip result).
- **Empty**: "No trips yet. Your trips show up here after you drive."
- **Desktop**: list left, the chosen trip with its route right.
- **API**: `GET /trips?from=&to=`.

---

## My cars

### 16. My cars
- **Shows**: the cars you offer, per car the photo, brand, license plate, status and a "Service due" badge.
- **Actions**: open a car (goes to Manage car), "Offer a car".
- **Empty**: "You don't offer a car yet." with "Offer a car".
- **API**: Needs backend: a way to list my own cars (`GET /cars` has no owner filter).

### 17. Add or edit car
- **Step 1, kind**: Electric, Combustion or Hydrogen. The kind cannot change after the car is made.
- **Step 2, basics**: Brand, License plate, Body style, Doors, Model year, Color, Transmission, Power, Mileage.
- **Step 3, energy**: combustion asks Fuel type, Tank size and Consumption per 100 km; electric asks Battery size and Consumption per 100 km; hydrogen asks Tank size and Consumption per 100 km.
- **Step 4, renting**: Rate per daypart, Location ("Use my current location" or pick on a map), and when you belong to an organization: "This car belongs to" (Me or the organization).
- **Step 5, costs (optional)**: Purchase price, Insurance per year, Road tax per year, Maintenance per year. Text: "Used to work out what the car costs you per year. You can add these later."
- **Buttons**: "Offer this car" or "Save changes". On edit also "Delete car" with confirmation "Delete Tesla Model 3 (AB-123-C)? This can't be undone."
- **Errors**: "A car with this license plate already exists." (409), and the server's message for each invalid field, shown under that field.
- **Mobile**: one step per screen with a progress bar. **Desktop**: one long form with the steps as sections.
- **API**: `POST /cars`, `PUT /cars/{id}`, `DELETE /cars/{id}`.

### 18. Manage car
- **Shows four sections**:
  - **Photos**: the current photos and "Add photo" (JPEG or PNG, up to 5 MB, up to 10 per car). Errors: "This file is too large. Use a photo under 5 MB.", "Use a JPEG or PNG.", "This car already has 10 photos."
  - **Service**: mileage, mileage at the last service, "Service is due" or "Next service in 3,200 km", and the button "Mark as serviced".
  - **Costs per year**: a field "Km per year" and the total cost of ownership for that distance.
  - **Trips in this car**: date, distance and score per trip.
- **Actions**: "Edit car", "View as renter" (opens Car detail).
- **Mobile**: the four sections as tabs. **Desktop**: a two by two grid.
- **API**: `POST /cars/{id}/images`, `PUT /cars/{id}/service`, `GET /cars/{id}/tco?kmPerYear=`, `GET /cars/{id}/trips`. Needs backend: deleting a photo.

---

## Insights

### 19. Leaderboard
- **Who**: everyone.
- **Shows**: rank, name, bonus points. Your own row is highlighted and pinned when it is off screen.
- **Empty**: "Nobody has driven yet. Be the first."
- **API**: `GET /leaderboard`.

### 20. My stats
- **Shows**: four numbers (trips, total km, average score, bonus points) and "You drive best in the evening."
- **Empty**: "Drive your first trip to see your stats."
- **Mobile**: on top of the Insights tab, above the leaderboard. **Desktop**: next to it.
- **API**: `GET /accounts/{id}/stats`.

---

## Account

### 21. Account settings
- **Shows**: name, email, bonus points.
- **Sections**:
  - **Profile**: Name, Email, "Save". Error: "There is already an account with this email."
  - **Password**: Current password, New password, "Change password". Errors: "The current password is wrong.", "Use at least 8 characters."
  - **Appearance**: System (default), Light, Dark. Changes at once, no save button.
  - **Organizations**: a link to page 22.
  - **Log out**.
  - **Delete account**: confirmation "Delete your account? Your reservations and trips are removed. This can't be undone." The person types their password to confirm.
- **API**: `PUT /accounts/{id}`, `PUT /accounts/{id}/password`, `POST /logout`, `DELETE /accounts/{id}`. Needs backend: "who am I".

### 22. Organizations
- **Shows**: the organizations you belong to, with your role in each.
- **Actions**: "Create organization" (one field, Name), "Join with a code" (one field, Invite code).
- **After joining**: "You joined Avans Rentals."
- **Errors**: "This code doesn't exist or was already used.", "You are already a member."
- **Empty**: "You are not in an organization. Create one for your rental company, or join one with an invite code."
- **API**: `POST /organizations`, `POST /invites/{code}/accept`. Needs backend: the list of my organizations.

### 23. Organization detail
- **Shows**: the name, the members with their role, the organization's cars.
- **Actions for an admin**: "Invite someone" (shows a one-time code with "Copy" and "Share", text: "This code works once and expires after 7 days."), change a member's role (Admin or Member), remove a member, "Fleet report".
- **Actions for everyone**: "Add a car" (opens Add car with the organization chosen), open a car, "Leave organization".
- **Errors**: "An organization needs at least one admin." (when the last admin leaves or is demoted).
- **Mobile**: tabs Members and Cars. **Desktop**: two columns.
- **API**: `POST /organizations/{id}/invites`, `PUT /organizations/{id}/members/{userId}`, `DELETE /organizations/{id}/members/{userId}`. Needs backend: the list of members and the list of the organization's cars.

### 24. Fleet report
- **Who**: admins.
- **Shows**: a table with one row per car: brand, license plate, reservations, trips, total km, average score, cost per year. A totals row at the bottom.
- **Actions**: sort by a column, "Download CSV".
- **Empty**: "This organization has no cars yet."
- **Mobile**: a card per car, since a wide table does not fit. **Desktop**: the table.
- **API**: `GET /organizations/{id}/report`, with `format=csv` for the download.

---

## Backend work this design needs

| # | What | For page |
|---|---|---|
| B1 | "Who am I": the logged in account from the token | 21, and every page that needs the account id |
| B2 | List my own cars | 16 |
| B3 | List my organizations with my role | 22 |
| B4 | List the members of an organization | 23 |
| B5 | List the cars of an organization | 23 |
| B6 | Store the route points of a trip and return them | 12, 13 |
| B7 | One trip by id, with its route | 13, 15 |
| B8 | Car brand and photo in a reservation row and a trip row | 10, 15 |
| B9 | Delete a car photo | 18 |
| B10 | Allow the web portal's origin (CORS) | web portal |

## Foundation work (not a page)

| # | What |
|---|---|
| F1 | App shell: bottom bar on mobile, sidebar on desktop, visitor and logged in variants |
| F2 | Shared pieces: car card, star rating, daypart picker, the loading, empty and error views |
| F3 | Login state: store the token safely per platform, handle an expired session |
| F4 | Location: permission flow, tracking during a trip, counting hard accelerations and brakes |
| F5 | Map: show a point (car) and a line (route) on all three platforms |
| F6 | Theme: the acid wave colors, type and shapes in a light and a dark mode, with the System, Light, Dark choice |

F4 and F5 carry the most risk and should be tried out before the pages that depend on them (5, 12, 13, 17).

## Suggested issues

One issue per page (24), one per backend item (10) and one per foundation item (6). Labels: `page`, `backend`, `foundation`, plus `mobile-only` for page 12. Each page issue links its two wireframes and lists the API calls and texts from this document as its checklist.

## Open questions

1. **Hard accelerations and brakes**: what counts as "hard"? The phone has to decide this from its sensors, so it needs a number (for example a speed change of more than 10 km/h in one second) and a test drive to tune it.
2. **Typing a trip by hand**: left out of the UI. Keep it out, or offer it on the web portal for people without the app?
3. **Language**: English texts here. Should the app be Dutch, or both?
4. **Car location**: the API stores coordinates only. Show it on a map only, or also as an address (needs an address lookup service)?
5. **Cars on a map** in Car search: not in this design. Worth adding once the map exists?
