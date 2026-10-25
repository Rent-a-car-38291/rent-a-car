# Tutorial: alles uitproberen met de console-app

*Deze tutorial is in het Nederlands; de rest van de documentatie in deze repository is in het Engels. De menu's en meldingen van de console-app zijn Engels, dus die staan hier ook in het Engels.*

Met deze tutorial loop je van boven naar beneden door **elke functie** van Rent my car, met de server en de console-app draaiend. Elke stap zegt wat je kiest, wat je invult en wat je te zien krijgt.

**Wat "geverifieerd" betekent.** Elke stap met ✅ heb ik zelf uitgevoerd, in deze volgorde, op een schone database, met de echte server en de echte console-app. De uitvoer in de blokken is overgenomen uit die run (waar nodig ingekort). Wat ik niet heb kunnen uitvoeren staat bij [Wat niet is getest](#wat-niet-is-getest).

| | |
|---|---|
| Getest op | macOS (Apple Silicon), Docker Desktop, server en client met `./kotlin` |

## Inhoud

0. [Voorbereiding](#0-voorbereiding)
1. [Starten](#1-starten)
2. [Zo werkt de console-app](#2-zo-werkt-de-console-app)
3. [Deel A: kijken als bezoeker](#deel-a-kijken-als-bezoeker)
4. [Deel B: een account](#deel-b-een-account)
5. [Deel C: auto's aanbieden en beheren](#deel-c-autos-aanbieden-en-beheren)
6. [Deel D: een tweede gebruiker huurt een auto](#deel-d-een-tweede-gebruiker-huurt-een-auto)
7. [Deel E: de eigenaar ziet de gevolgen](#deel-e-de-eigenaar-ziet-de-gevolgen)
8. [Deel F: organisaties](#deel-f-organisaties)
9. [Deel G: fouten en beveiliging](#deel-g-fouten-en-beveiliging)
10. [Deel H: een account verwijderen](#deel-h-een-account-verwijderen)
11. [Deel I: zonder de console (curl en Swagger)](#deel-i-zonder-de-console-curl-en-swagger)
12. [Problemen oplossen](#problemen-oplossen)
13. [Wat niet is getest](#wat-niet-is-getest)
14. [Checklist](#checklist)

---

## 0. Voorbereiding

Je hebt nodig:

- **Git**, om de repository te clonen.
- **Docker** (Docker Desktop is prima), voor de database. Zorg dat Docker draait: `docker info` moet een "Server Version" laten zien.
- **Geen JDK.** De wrapper `./kotlin` haalt zelf de toolchain en een passende JDK op bij de eerste keer. Dat kost een minuut of twee en daarna niet meer. (Op de testmachine stond JDK 24 geïnstalleerd, terwijl het project JDK 25 gebruikt; dat ging vanzelf goed.)
- **Een tweede terminalvenster.** De server blijft draaien in het eerste, de console-app draait in het tweede.

Op Windows gebruik je `kotlin.bat` in plaats van `./kotlin`. Dat heb ik niet getest.

```bash
git clone https://github.com/Rent-a-car-38291/rent-a-car.git
cd rent-a-car
```

## 1. Starten

✅ **Terminal 1: database en server**

```bash
docker compose up -d
./kotlin run -m rent-a-car
```

`-m rent-a-car` kiest de server (het project heeft twee modules). De map waarin je clonet bepaalt de naam van de servermodule; `./kotlin show modules` toont de namen. Wacht tot je dit ziet:

```
INFO  Application - Application started in 0.3 seconds.
INFO  Application - Responding at http://0.0.0.0:8080
```

✅ **Controleer dat de server antwoordt** (in een derde venster of na de server te hebben laten draaien):

```bash
curl -i 'localhost:8080/cars?limit=1'
```

Je krijgt `HTTP/1.1 200 OK`, een header `X-Total-Count: 13` en één auto in JSON. Er staan 13 voorbeeldauto's in de database.

✅ **Terminal 2: de console-app**

```bash
./kotlin run -m cli
```

```
Rent-a-car console client. Type 'back' at any prompt to cancel the current action.

== Rent-a-car ==
  1) Register
  2) Log in
  3) Browse as a visitor (no login)
  0) Quit
Choose:
```

Een andere server? `RENT_A_CAR_URL=http://localhost:9000 ./kotlin run -m cli`.

**Alles opnieuw beginnen** (schone database en schone foto's):

```bash
docker compose down -v      # verwijdert de database
rm -rf uploads              # verwijdert de foto's (de map uit IMAGES_DIRECTORY, standaard `uploads`)
docker compose up -d        # daarna de server opnieuw starten
```

## 2. Zo werkt de console-app

- **Menu's** zijn genummerde lijsten. Typ het nummer en Enter. `0` gaat een niveau omhoog (in het hoofdmenu: uitloggen; op het startscherm: afsluiten).
- **`back`** (of `b`) bij elke vraag breekt de actie af: `(cancelled)`.
- **Enter** neemt de standaardwaarde tussen de `[ ]`, of slaat een optioneel veld over (`Enter to skip`).
- **Auto's kies je uit een lijst**: je typt het nummer van de regel. Het nummer van de regel is niet altijd het id van de auto: de regel zegt `#21 Tesla Model Y`. Bij meer dan 20 auto's staat onderaan `More cars...`.
- **Lange lijsten** worden per pagina getoond: `Showing 20 of 22.` en `Show the next page? (y/N)`.
- **Id's in deze tutorial** (`#14`, `#21`, `reservation #7`) kunnen bij jou anders zijn: mislukte pogingen (bijvoorbeeld een kenteken dat al bestaat) en verwijderde auto's gebruiken ook een nummer, dus bij jou is het nummer vaak lager of anders. De client toont altijd het id dat je nodig hebt; neem dat over.
- **Het account-id**: de server heeft geen "wie ben ik". Na *Register* kent de client je id. Na *Log in* vraagt hij het één keer, bij de eerste actie die het nodig heeft (`Your account id (shown when you registered)`). Onthoud dus het id dat je bij het registreren ziet.

### De menu's op een rij

| Startscherm | | Hoofdmenu (ingelogd) | |
|---|---|---|---|
| 1 Register | 2 Log in | 1 Cars | 5 Insights |
| 3 Browse as a visitor | 0 Quit | 2 Costs | 6 Account |
| | | 3 Reservations | 7 Organizations |
| | | 4 Trips | 0 Log out |

Als **bezoeker** zie je alleen de menu's die geen login nodig hebben: `1 Cars`, `2 Costs`, `3 Insights`.

| Menu | Keuzes (ingelogd) |
|---|---|
| **Cars** | 1 List and search · 2 Car details, photos and ratings · 3 Is a car free? · 4 Offer my car · 5 Update a car I manage · 6 Delete a car I manage · 7 Mark a car as serviced · 8 Add a photo |
| **Costs** | 1 Energy cost per km · 2 Quote a trip · 3 Compare car types · 4 Total cost of ownership |
| **Reservations** | 1 Reserve a car · 2 My reservations · 3 Cancel a reservation |
| **Trips** | 1 Start my trip · 2 End my trip · 3 Record a finished trip in one go · 4 My trip history · 5 Trips with a car I manage · 6 Rate the car |
| **Insights** | 1 Leaderboard · 2 My driving stats |
| **Account** | 1 Update my name and email · 2 Change my password · 3 Delete my account |
| **Organizations** | 1 Create · 2 Invite someone · 3 Join with an invite code · 4 Change a member's role · 5 Remove a member, or leave · 6 Add an organization car · 7 Fleet report |

Hieronder schrijf ik een keuze als `Cars › Offer my car` (menu, dan item).

### Het autoformulier

*Cars › Offer my car* en *Organizations › Add an organization car* stellen deze vragen, in deze volgorde. **Enter** neemt de standaardwaarde.

| Vraag | Voorbeeld | Opmerking |
|---|---|---|
| Type | `1` | 1 = BEV (elektrisch), 2 = ICE (benzine, diesel, LPG), 3 = FCEV (waterstof) |
| Brand | `Polestar 2` | |
| License plate | `AN-001-A` | moet uniek zijn |
| Mileage in km | `12000` | |
| Rate in EUR | `49.5` | huurtarief |
| Latitude / Longitude | Enter, Enter | standaard 51.59 / 4.78 |
| Doors | Enter | 5 |
| Body style | Enter | 1 = Hatchback |
| Model year / Power in kW | Enter, Enter | 2024 / 100 |
| Transmission | `2` | 1 = Manual, 2 = Automatic |
| Color | `Grey` | |
| Fuel | `1` | **alleen bij ICE**: 1 Petrol, 2 Diesel, 3 LPG |
| Tank or battery size | `78` | kWh (BEV), liters (ICE) of kg (FCEV) |
| Consumption per 100 km | `17.5` | in dezelfde eenheid |
| Purchase price / Insurance / Road tax / Maintenance | `45000`, `900`, `0`, `600` | voor de TCO: de aankoopprijs is eenmalig (de server verdeelt hem over 10 jaar), de rest is per jaar; Enter slaat een bedrag over (0) |

---

## Deel A: kijken als bezoeker

Je hoeft niet in te loggen om auto's te bekijken. Kies op het startscherm `3` (Browse as a visitor), dan `1` (Cars).

### A1 ✅ Alle auto's bekijken

**Kies:** `Cars › List and search cars`. Er komen negen vragen (filters). Druk negen keer **Enter**.

```
  id  type  brand            licensePlate  rate  status       averageRating  location.latitude  location.longitude
  --  ----  ---------------  ------------  ----  -----------  -------------  -----------------  ------------------
  1   ICE   Peugeot          85-GHB-2      25.0  Available    -              10.0               29.294
  2   BEV   Tesla Model 3    K-123-BV      55.0  Available    -              52.3676            4.9041
  ...
  7   ICE   Ford Focus       ZV-117-P      30.0  Maintenance  -              53.2194            6.5665
  ...
  13  FCEV  Toyota Mirai     VP-912-N      72.0  Available    -              52.3874            4.6462
```

13 auto's, van drie typen. De Ford Focus staat in onderhoud.

### A2 ✅ Filteren en sorteren

**Kies:** `Cars › List and search cars`, vul in: Type `bev`, Highest rate `60`, de rest Enter, bij *Sort by* `-rate` (het minteken draait de volgorde om).

```
  id  type  brand          licensePlate  rate  status     averageRating  ...
  2   BEV   Tesla Model 3  K-123-BV      55.0  Available  -
  11  BEV   Hyundai Kona   XB-370-F      52.0  Available  -
  9   BEV   Nissan Leaf    N-641-GH      38.0  Available  -
  10  BEV   Renault Zoe    G-208-RT      32.0  Available  -
```

Je kunt ook filteren op carrosserie (`Stationwagon`), transmissie, brandstof en minimale actieradius, en sorteren op `rate`, `range` of `costPerKm`. Een ongeldig type (`xyz`) geeft: `! The server did not accept the filters. The type is BEV, ICE or FCEV, ...`. ✅

### A3 ✅ Dichtstbij

**Kies:** `Cars › List and search cars`. Latitude `52.37`, Longitude `4.90`, Furthest distance `50`, de rest Enter.

```
  2   BEV   Tesla Model 3    K-123-BV      55.0  ...  52.3676  4.9041
  13  FCEV  Toyota Mirai     VP-912-N      72.0  ...  52.3874  4.6462
  4   ICE   Volkswagen Golf  H-482-LK      35.0  ...  52.0907  5.1214
  8   ICE   Mazda MX-5       R-905-ZB      60.0  ...  52.1601  4.497
```

Het dichtstbijzijnde staat bovenaan.

### A4 ✅ Alleen auto's die vrij zijn op een dag en dagdeel

**Kies:** `Cars › List and search cars`. Bij *Free on date* vul je de datum van **morgen** in. Typ in de terminal eerst `date -v+1d +%Y-%m-%d` (macOS) of `date -d tomorrow +%Y-%m-%d` (Linux) om die te zien. Daarna vraagt de client het dagdeel: `2` (AFTERNOON). De rest Enter.

Je ziet 12 auto's: de Ford Focus ontbreekt, want die is in onderhoud. Zodra auto's gereserveerd zijn (deel D) ontbreken ook die.

### A5 ✅ Is een auto vrij?

**Kies:** `Cars › Is a car free? (availability on a date)`, auto `2`, datum Enter (vandaag).

```
  Free dayparts on 2026-10-25: MORNING, AFTERNOON, EVENING
```

### A6 ✅ Kosten

Ga terug (`0`) naar het hoofdmenu en kies `2` (Costs).

**Energy cost per km of a car** (auto `2`, de Tesla):

```
  EUR 0.0507 per km
```

**Quote a trip before reserving** (auto `2`, afstand `120`):

```
  distanceKm  120.0
  energyCost  6.09
  rentalRate  55.0
  total       61.09
  currency    EUR
```

Dat is `120 × 0,05075 = 6,09` energiekosten plus het huurtarief van 55. (`EUR 0.0507 per km` hierboven is 0,05075 afgerond op vier decimalen.)

**Compare car types** (afstand `100`):

```
  type  cars  averageCost  co2Kg
  ----  ----  -----------  -----
  BEV   4     5.46         0.0
  ICE   6     11.01        13.06
  FCEV  3     9.88         0.0
```

Per type de gemiddelde kosten en de CO2-uitstoot voor die afstand. Elektrisch en waterstof tellen als 0 kg omdat alleen de uitlaat meetelt.

### A7 ✅ De ranglijst

Terug naar het hoofdmenu, `Insights › Leaderboard`, *Drivers per page* Enter. Nog niemand heeft punten:

```
  (nothing to show)
```

Ga met `0`, `0` terug naar het startscherm.

---

## Deel B: een account

### B1 ✅ Registreren

**Kies:** `1` (Register). Name `Anna Jansen`, Email `anna@example.com`, Password `supersecret`.

```
Registered! Your account id is 1.
Welcome! You are logged in.
```

Onthoud het id (hier `1`). Een wachtwoord heeft 8 tot 128 tekens.

### B2 ✅ Naam en e-mailadres wijzigen

**Kies:** `Account › Update my name and email`. New name `Anna J. Jansen`, New email `anna@example.com`.

```
Account updated.
```

Een ongeldig e-mailadres (`x`) geeft: `! Enter a name and a valid email address.` ✅ (In deel D zie je ook het geval "e-mailadres is al van een ander".)

### B3 ✅ Wachtwoord wijzigen

**Kies:** `Account › Change my password`. Current password `wrong-pass`, New password `newsecret1`:

```
! Wrong current password, or that is not your account id.
```

Doe het opnieuw met Current password `supersecret`:

```
Password changed. Your other logins were ended.
```

### B4 ✅ Uitloggen en weer inloggen

Ga met `0`, `0` terug: `Logged out.` Kies `2` (Log in) en log in met `anna@example.com` en `newsecret1`:

```
Welcome! You are logged in.
```

---

## Deel C: auto's aanbieden en beheren

Je bent ingelogd als Anna (id 1).

### C1 ✅ Drie auto's aanbieden, één van elk type

**Kies:** `Cars › Offer my car` en vul het [autoformulier](#het-autoformulier) in, drie keer:

| | Type | Brand | Plate | Mileage | Rate | Transmission | Color | Fuel | Size | Consumption | Kosten (aankoop, verzekering, wegenbelasting, onderhoud) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `1` BEV | Polestar 2 | AN-001-A | 12000 | 49.5 | `2` | Grey | | 78 | 17.5 | 45000, 900, 0, 600 |
| 2 | `2` ICE | Opel Corsa | AN-002-B | 80000 | 22 | Enter (1) | Red | `1` | 44 | 5.6 | 12000, 700, 900, 500 |
| 3 | `3` FCEV | Hyundai Nexo | AN-003-C | 5000 | 80 | `2` | White | | 6.3 | 0.95 | Enter ×4 |

Telkens `Car #14 added.`, `Car #15 added.`, `Car #16 added.` (de 13 voorbeeldauto's hebben id 1 t/m 13).

Maak nog een vierde auto, die we later weer verwijderen: BEV, Brand `Temp car`, Plate `AN-004-D`, Mileage `1`, Rate `1`, Color `Red`, Size `40`, Consumption `15`, de rest Enter.

### C2 ✅ Een kenteken dat al bestaat

**Kies:** `Cars › Offer my car`, type `2`, Plate `AN-001-A`:

```
! Another car already has this license plate.
```

### C3 ✅ Details van een auto

**Kies:** `Cars › Car details, photos and ratings`, de Polestar (regel 14).

```
  type                    BEV
  id                      14
  ownerId                 1
  brand                   Polestar 2
  licensePlate            AN-001-A
  mileage                 12000
  status                  Available
  rate                    49.5
  ...
  batteryCapacityKwh      78.0
  consumptionKwhPer100Km  17.5
  purchasePrice           45000.0
  insurancePerYear        900.0
  roadTaxPerYear          0.0
  maintenancePerYear      600.0
  organizationId          -
  lastServiceKm           12000
  serviceDue              false
  averageRating           -
  photos: none
  rating: no ratings yet
```

### C4 ✅ Een auto aanpassen

**Kies:** `Cars › Update a car I manage`, de Polestar. De client toont de huidige waarden tussen `[ ]`. Druk Enter bij alles, behalve: Rate `52` en Color `Silver`.

```
Car #14 updated.
```

### C5 ✅ Onderhoud

De Opel Corsa (regel 15) heeft 80.000 km. Maak er 100.000 van: `Cars › Update a car I manage`, de Opel, Mileage `100000`, de rest Enter. Open daarna de details van de Opel:

```
  mileage                    100000
  lastServiceKm              80000
  serviceDue                 true
```

Een auto heeft onderhoud nodig na meer dan 15.000 km sinds de laatste beurt. Kies nu `Cars › Mark a car as serviced`, de Opel:

```
Car #15 is marked as serviced at its current mileage.
```

`serviceDue` is nu `false` en `lastServiceKm` is 100000. Een kilometerstand lager dan de laatste onderhoudsbeurt wordt geweigerd:

```
! The server did not accept the values. The mileage cannot be lower than the mileage at the last service.
```

(probeer: update de Opel met Mileage `100`.)

### C6 ✅ Foto's toevoegen

Maak een klein PNG-bestand (1×1 pixel), zodat je geen eigen foto nodig hebt. Doe dit in een gewone terminal, in een map waar je schrijfrechten hebt:

```bash
printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' | base64 -d > tiny.png
echo "geen plaatje" > notities.txt
pwd    # dit pad heb je zo nodig
```

**Kies:** `Cars › Add a photo to a car I manage`, de Polestar. Vul bij *Path of the JPEG or PNG file* het volledige pad in, bijvoorbeeld `/Users/jij/rent-a-car/tiny.png`:

```
Photo added: /images/25453d49-3563-4ebb-bc6a-19057d256dc9.png
```

De client controleert het bestand zelf voordat hij het stuurt. Probeer ook:

| Pad | Melding |
|---|---|
| het pad van `notities.txt` | `! That is not a JPEG or PNG picture.` |
| `/bestaat/niet.png` | `! There is no file at /bestaat/niet.png.` |
| een map | `! ... is a folder, give the path of a picture.` |

Een pad dat je uit je bestandsbeheer naar de terminal sleept (met aanhalingstekens of `\ ` voor spaties) werkt ook. De server staat maximaal 5 MB toe, 10 foto's per auto, alleen JPEG of PNG. De details van de Polestar tonen nu `photos: /images/...png`.

### C7 ✅ Een auto verwijderen

**Kies:** `Cars › Delete a car I manage`, kies de `Temp car` (regel 17). Antwoord eerst `n`: er gebeurt niets. Doe het nog eens en antwoord `y`:

```
Delete car #17 for good? (y/N):
Car #17 deleted.
```

### C8 ✅ De auto van iemand anders

Anna probeert een voorbeeldauto te bewerken. `Cars › Update a car I manage`, auto 1 (Peugeot), Enter bij alles:

```
! You are not allowed to do this.
```

Hetzelfde geldt voor `Delete a car I manage` (met `y`) en `Mark a car as serviced`.

### C9 ✅ Kosten van je eigen auto (TCO)

Terug naar het hoofdmenu, `Costs › Total cost of ownership per year (my car)`, de Polestar, *Km per year* Enter (15.000):

```
  kmPerYear   15000
  tcoPerYear  6918.75
  currency    EUR
```

Dat is afschrijving over 10 jaar (4.500) plus verzekering (900), wegenbelasting (0), onderhoud (600) en de energiekosten voor 15.000 km (918,75). Probeer ook `30000`:

```
  kmPerYear   30000
  tcoPerYear  7837.5
```

De TCO van de Peugeot (niet van Anna) geeft:

```
! Only the owner (or an organization member) can see the TCO of a car.
```

Log uit (`0`, `0`).

---

## Deel D: een tweede gebruiker huurt een auto

### D1 ✅ Ben registreert

Op het startscherm: `1` (Register). Name `Ben de Vries`, Email `ben@example.com`, Password `supersecret`:

```
Registered! Your account id is 2.
```

Een e-mailadres dat al bestaat kan niet. `Account › Update my name and email`, New name `Ben de Vries`, New email `anna@example.com`:

```
! That email address is already in use.
```

### D2 ✅ Beschikbaarheid

`Cars › Is a car free?`, de Polestar (14), datum Enter:

```
  Free dayparts on 2026-10-25: MORNING, AFTERNOON, EVENING
```

### D3 ✅ Reserveren

Een reservering is voor een dagdeel: MORNING (06-12 uur), AFTERNOON (12-18) of EVENING (18-24), in Nederlandse tijd. Je kunt alle dagdelen van vandaag reserveren, ook als het dagdeel al begonnen is.

**Kies:** `Reservations › Reserve a car` en vul in:

| Auto | Datum | Dagdeel | Resultaat |
|---|---|---|---|
| 14 (Polestar) | Enter (vandaag) | `1` MORNING | `Reserved: reservation #1 for car 14 on 2026-10-25, MORNING.` |
| 15 (Opel) | Enter | `2` AFTERNOON | `Reserved: reservation #2 for car 15 ...` |
| 16 (Nexo) | Enter | `3` EVENING | `Reserved: reservation #3 for car 16 ...` |

Nu de fout-gevallen:

| Auto | Datum | Dagdeel | Melding |
|---|---|---|---|
| 14 | Enter | `1` | `! That car is not free then (already reserved, or in maintenance). Check the availability.` |
| 14 | gisteren | `2` | `! The date cannot be in the past.` |
| 7 (Ford Focus, onderhoud) | morgen | `2` | `! That car is not free then (already reserved, or in maintenance). Check the availability.` |

Reserveer tot slot de Polestar voor **morgen** AFTERNOON (die annuleren we zo):

```
Reserved: reservation #7 for car 14 on 2026-10-26, AFTERNOON.
```

(Het id springt, omdat de mislukte pogingen ook een nummer gebruikten.)

### D4 ✅ Mijn reserveringen

**Kies:** `Reservations › My reservations`, status Enter:

```
  id  carId  date        daypart    status
  --  -----  ----------  ---------  --------
  7   14     2026-10-26  AFTERNOON  RESERVED
  3   16     2026-10-25  EVENING    RESERVED
  2   15     2026-10-25  AFTERNOON  RESERVED
  1   14     2026-10-25  MORNING    RESERVED
```

Nieuwste datum eerst. Met een statusfilter (`RESERVED`, `COMPLETED`, `CANCELLED`) zie je er een deel van; een andere waarde geeft `! The status must be RESERVED, COMPLETED or CANCELLED.`

### D5 ✅ Annuleren

**Kies:** `Reservations › Cancel a reservation`. De lijst bevat de reserveringen die je in deze sessie maakte. Kies de reservering voor morgen (`#7`):

```
Reservation #7 cancelled.
```

`My reservations` met status `CANCELLED` toont hem nog, met status CANCELLED: hij is bewaard. Een annulering kan alleen zolang het dagdeel nog niet begonnen is en er geen rit is (zie D8 voor de weigering).

### D6 ✅ Zoeken naar vrije auto's

**Kies:** `Cars › Is a car free?`, de Polestar, datum Enter:

```
  Free dayparts on 2026-10-25: AFTERNOON, EVENING
```

En `Cars › List and search cars` met *Free on date* = vandaag, dagdeel `1` (MORNING), de rest Enter: je ziet 14 auto's, **zonder** de Polestar (gereserveerd voor die ochtend) en zonder de Ford Focus (onderhoud).

### D7 ✅ Een rit rijden

**Kies:** `Trips › Start my trip` en kies reservering `#1`:

```
Trip started at 16:35. Drive safely, then end it here.
```

(De tijd is Nederlandse tijd.) Start je nog een keer:

```
! A trip can only be started on the day of the reservation (Dutch date), and only once.
```

**Kies:** `Trips › End my trip`, reservering `#1`. Distance `120`, Hard accelerations `2`, Hard brakes `1`:

```
  trip.distanceKm       120
  trip.score            88
  trip.durationMinutes  1
  pointsAwarded         105
  bonusPoints           105
```

De server rekent de score uit: 100 punten, min 5 per harde actie per 100 km (3 acties op 120 km geeft 87,5, afgerond op 88). De bonuspunten zijn afstand × score ÷ 100, naar beneden afgerond (120 × 0,88 = 105). Nog een keer beëindigen: `! Start the trip first, and a trip can only be ended once.`

### D8 ✅ Een rit in één keer opslaan

**Kies:** `Trips › Record a finished trip in one go`, reservering `#1` (die heeft al een rit):

```
! Not possible now: the reservation has to be on today or earlier, and a trip can be started and ended once.
```

Nu reservering `#2` (de Opel): Distance `80`, de rest `0`:

```
  trip.distanceKm       80
  trip.score            100
  trip.durationMinutes  -
  pointsAwarded         80
  bonusPoints           185
```

Een rit kan alleen bij een reservering van vandaag of eerder, op elk dagdeel van die dag.

**Een reservering met een rit kan niet geannuleerd worden.** `Reservations › Cancel a reservation`, reservering `#1`:

```
! It cannot be cancelled: the daypart has started, there is a trip, or it is already completed or cancelled.
```

Dit werkt op elk tijdstip van de dag, omdat er een rit is.

### D9 ✅ De auto beoordelen

**Kies:** `Trips › Rate the car after a finished trip`, reservering `#2`. Stars `5`, Comment `Smooth and clean`:

```
Thanks for rating!
```

Opnieuw beoordelen, of reservering `#3` (nog geen rit) beoordelen:

```
! Rating is only possible once, after a finished trip.
```

### D10 ✅ Ritgeschiedenis, pagina voor pagina

**Kies:** `Trips › My trip history`. From en To Enter, *Trips per page* `1`:

```
  date        daypart    carId  distanceKm  score  durationMinutes
  ----------  ---------  -----  ----------  -----  ---------------
  2026-10-25  AFTERNOON  15     80          100    -
  Showing 1 of 2.
Show the next page? (y/N):
```

Antwoord `y` voor de volgende pagina (de rit van 120 km). Je kunt ook van/tot-data invullen.

### D11 ✅ Ritten van een auto die je niet beheert

`Trips › Trips with a car I manage`, de Polestar:

```
! Only the owner of a car (or an organization member) can see its trips.
```

### D12 ✅ Je statistieken en de ranglijst

`Insights › My driving stats`:

```
  tripCount     2
  totalKm       200
  averageScore  94.0
  bestDaypart   AFTERNOON
  bonusPoints   185
```

`Insights › Leaderboard`, *Drivers per page* Enter:

```
  name          bonusPoints
  ------------  -----------
  Ben de Vries  185
```

Log uit (`0`, `0`).

---

## Deel E: de eigenaar ziet de gevolgen

Log in als Anna (`anna@example.com`, `newsecret1`).

### E1 ✅ De ritten van mijn auto

`Trips › Trips with a car I manage`, de Polestar:

```
  date        daypart  distanceKm  score  durationMinutes
  ----------  -------  ----------  -----  ---------------
  2026-10-25  MORNING  120         88     1
```

### E2 ✅ De beoordeling van mijn auto

`Cars › Car details, photos and ratings`, de Opel:

```
  averageRating              5.0
  ...
  rating: 5.0 from 1 rating(s)
  stars  comment
  -----  ----------------
  5      Smooth and clean
```

De gemiddelde beoordeling staat ook in de autolijst (kolom `averageRating`).

### E3 ✅ Statistieken zonder ritten

`Insights › My driving stats`. De client vraagt je id (je bent ingelogd zonder te registreren): `1`.

```
  tripCount     0
  totalKm       0
  averageScore  -
  bestDaypart   -
  bonusPoints   0
```

Anna reed zelf niet, dus alles is leeg.

---

## Deel F: organisaties

Een organisatie is een verhuurbedrijf. De maker is beheerder (`ADMIN`); anderen zijn lid (`MEMBER`). Voor dit deel heb je drie gebruikers nodig: Anna (beheerder), Ben (lid) en Carla (buitenstaander, later lid). Doe het met drie terminalvensters of log tussendoor uit en in.

### F1 ✅ Anna maakt een organisatie, een uitnodiging en een organisatieauto

Log in als Anna. **Kies:** `Organizations › Create an organization`, Name `Anna Autoverhuur`:

```
Organization #1 created.
```

`Organizations › Invite someone (admin): create an invite code`, organisatie `#1`:

```
Invite code (works once, 7 days): b120a27b-4afc-4c00-b58d-bfcd3c5fed19
```

Bewaar die code. `Organizations › Add an organization car`, organisatie `#1`, autoformulier: BEV, Brand `Tesla Model Y`, Plate `ORG-001-A`, Mileage `3000`, Rate `65`, Transmission `2`, Color `Black`, Size `75`, Consumption `16`, kosten `48000, 1200, 0, 700`:

```
Car #21 added.
```

Een organisatieauto heeft geen eigenaar maar hoort bij de organisatie.

`Organizations › Fleet report`, organisatie `#1`:

```
  carId  brand          licensePlate  reservations  trips  totalKm  averageScore  tcoPerYear
  -----  -------------  ------------  ------------  -----  -------  ------------  ----------
  21     Tesla Model Y  ORG-001-A     0             0      0        -             7540.0
```

Log uit.

### F2 ✅ Ben wordt lid en wat hij mag

Log in als Ben. `Organizations › Join with an invite code`, plak de code:

```
You joined the organization as a member.
```

Dezelfde code nog een keer: `! Unknown, used or expired code.` (een code werkt één keer).

Wat een lid niet mag (de client vraagt het organisatie-id, `1`, omdat Ben de organisatie niet in deze sessie maakte):

| Keuze | Melding |
|---|---|
| `Invite someone` | `! Only an admin of the organization can invite.` |
| `Change a member's role` (organisatie `1`, account `1`, rol `2`) | `! Only an admin of the organization can do that.` |
| `Cars › Delete a car I manage`, de Tesla Model Y (regel 17), `y` | `! You are not allowed to do this.` |

Wat een lid wel mag:

- `Organizations › Fleet report`, organisatie `1`: je ziet de Tesla.
- `Organizations › Add an organization car`, organisatie `1`: bijvoorbeeld `Ben org car`, `BEN-ORG-1` → `Car #22 added.`
- `Cars › Update a car I manage`, de Tesla (regel 17): bijvoorbeeld Rate `70` → `Car #21 updated.`
- `Reservations › Reserve a car`, de Tesla, morgen, `2` → `Reserved: reservation #8 ...`. Het rapport telt de reservering mee:

```
  21     Tesla Model Y  ORG-001-A     1             0      0        -             7540.0
  22     Ben org car    BEN-ORG-1     0             0      0        -             787.5
```

Log uit.

### F3 ✅ De laatste beheerder, en een lid beheerder maken

Log in als Anna (organisaties kent de client niet bij inloggen: je typt het id `1`).

| Keuze | Melding |
|---|---|
| `Change a member's role`: organisatie `1`, account `1` (jezelf), rol `2` (MEMBER) | `! The last admin cannot be demoted.` |
| `Remove a member, or leave`: organisatie `1`, account `1` | `! The last admin cannot leave.` |
| `Account › Delete my account`, `y`, daarna het account-id `1` | `! You are the last admin of an organization. Make someone else admin first.` |

Maak Ben beheerder: `Change a member's role`, organisatie `1`, account `2`, rol `1` (ADMIN):

```
Account 2 is now ADMIN.
```

Maak een nieuwe uitnodiging voor Carla (`Invite someone`, organisatie `1`) en bewaar de code. Log uit.

### F4 ✅ Carla: buitenstaander, daarna lid

Registreer `Carla Bakker` (`carla@example.com`, `supersecret`; id 3). Als buitenstaander:

| Keuze | Melding |
|---|---|
| `Join with an invite code` met `not-a-real-code` | `! Unknown, used or expired code.` |
| `Fleet report`, organisatie `1` | `! Only members of the organization can see its report.` |
| `Add an organization car`, organisatie `1` (met een autoformulier) | `! You are not a member of that organization.` |

Word lid met de nieuwe code: `You joined the organization as a member.` Het rapport werkt nu. Reserveer daarna auto `2` (Tesla Model 3) voor vandaag (dagdeel `1`) en sla een rit van `60` km in één keer op (`Trips › Record a finished trip in one go`): `pointsAwarded 60`. Zo staat Carla straks ook op de ranglijst. Log uit.

### F5 ✅ Een beheerder verwijdert een lid en een organisatieauto

Log in als Ben (nu beheerder). `Remove a member, or leave`, organisatie `1`, account `3`:

```
Account 3 is no longer a member.
```

`Cars › Delete a car I manage`, de `Ben org car` (regel 18), `y` (een beheerder mag organisatieauto's verwijderen):

```
Car #22 deleted.
```

### F6 ✅ Weggaan

Anna is niet meer de enige beheerder, dus ze mag weg. Log in als Anna: `Remove a member, or leave`, organisatie `1`, account `1`:

```
Account 1 is no longer a member.
```

Het rapport daarna: `! Only members of the organization can see its report.`

---

## Deel G: fouten en beveiliging

### G1 ✅ Registreren: ongeldige invoer en dubbel adres

Op het startscherm, `1` (Register):

| Invoer | Melding |
|---|---|
| Password `abc` | `! Enter a name, a valid email address and a password of 8 to 128 characters.` |
| Email `ben@example.com` (bestaat al) | `! That email address is already registered. Log in instead.` |

### G2 ✅ Inloggen wordt geblokkeerd na vijf fouten

Registreer `Dirk Smit`, `dirk@example.com`, `supersecret`, en log weer uit. Kies vijf keer `2` (Log in) met een fout wachtwoord:

```
! Wrong email or password.
```

De zesde keer, nu mét het juiste wachtwoord:

```
! Too many failed login attempts for that email. Try again in about 15 minutes.
```

De server stuurt `429 Too Many Requests` met een `Retry-After`-header (900 seconden als je de pogingen snel doet; typ je ze langzaam, dan zie je 14 of 13 minuten, want de 15 minuten lopen vanaf de eerste poging). Een geslaagde login zet de teller terug op nul. De teller staat in het geheugen van de server: een herstart van de server maakt de blokkade ongedaan, zo kun je Dirk weer gebruiken.

### G3 ✅ Een inlog overleeft een herstart van de server

Log in als Carla (`carla@example.com`, `supersecret`) en laat de client open staan op het hoofdmenu. Stop de server in terminal 1 (Ctrl-C). Kies in de client `Reservations › My reservations`:

```
! Cannot reach the server at http://localhost:8080. Is it running?
```

Start de server opnieuw (`./kotlin run -m rent-a-car`) en kies *My reservations* opnieuw in **dezelfde** client: je ziet gewoon je reservering. Je hoeft niet opnieuw in te loggen, want tokens staan in de database (alleen de hash) en zijn 7 dagen geldig.

### G4 ✅ Een ingetrokken token

Laat de eerste client van G3 openstaan. Open een tweede client en verander Carla's wachtwoord (`Account › Change my password`, id `3`, huidig `supersecret`, nieuw `carla-new-1`): `Password changed. Your other logins were ended.` Kies nu in de **eerste** client weer *My reservations*:

```
! Your login has expired or was ended elsewhere. Log in again.

== Rent-a-car ==
  1) Register
  2) Log in
  3) Browse as a visitor (no login)
  0) Quit
```

Je staat terug op het startscherm.

### G5 ✅ De server is niet bereikbaar

Start de client met een adres waar niets draait: `./kotlin run -m cli -- http://localhost:9999` (of `RENT_A_CAR_URL=http://localhost:9999 ./kotlin run -m cli`). Kies `3`, `1`, `1` en druk negen keer Enter (de filtervragen):

```
! Cannot reach the server at http://localhost:9999. Is it running?
```

### G6 ✅ Annuleren met `back`

Kies `Reservations › Reserve a car` en typ bij een vraag `back`:

```
  (cancelled)
```

---

## Deel H: een account verwijderen

### H1 ✅ Anna verwijdert haar account

Log in als Anna: `Account › Delete my account`, antwoord `y`, id `1`:

```
Account deleted.
```

### H2 ✅ Wat er daarna met de gegevens gebeurt

Log in als Ben:

- **De auto's van Anna zijn weg** (de Polestar, Opel en Nexo): in *List and search cars* staan alleen de 13 voorbeeldauto's en de organisatieauto (`#21 Tesla Model Y`). Een organisatieauto blijft, want hij had geen eigenaar.
- **Reserveringen en ritten op die auto's zijn ook weg**, dus ook Ben's ritgeschiedenis:

```
  tripCount     0
  totalKm       0
  averageScore  -
  bestDaypart   -
  bonusPoints   185
```

De bonuspunten blijven staan, maar de ritten zelf zijn weg. Dat is het gedrag van de server nu: wie een auto of account verwijdert, verwijdert ook de reserveringen en ritten die daaraan hangen. Of dat voor jouw gebruik gewenst is, is een ontwerpkeuze; zie [Wat niet is getest](#wat-niet-is-getest).

- **De ranglijst** (`Insights › Leaderboard`, *Drivers per page* `1`):

```
  name          bonusPoints
  ------------  -----------
  Ben de Vries  185
  Showing 1 of 2.
Show the next page? (y/N):
```

Met `y` zie je Carla Bakker met 60 punten.

- `Reservations › My reservations` toont alleen nog de reservering voor de organisatieauto.

---

## Deel I: zonder de console (curl en Swagger)

De console-app dekt alles wat de API biedt, behalve het CSV-formaat van het fleetrapport en het ophalen van een foto; de API heeft geen lijst van de leden van een organisatie. Dit zijn handige alternatieven.

### Swagger UI ✅

Open `http://localhost:8080/swagger` (zonder slash aan het eind). Je ziet alle endpoints en kunt ze uitproberen met "Try it out". Swagger staat alleen aan in ontwikkelmodus; met `KTOR_DEVELOPMENT=false` staat het uit.

### Een token halen en gebruiken ✅

```bash
TOKEN=$(curl -s -X POST localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"email":"ben@example.com","password":"supersecret"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
```

### Het organisatierapport als CSV ✅

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/organizations/1/report?format=csv'
```

```
carId,brand,licensePlate,reservations,trips,totalKm,averageScore,tcoPerYear
21,Tesla Model Y,ORG-001-A,1,0,0,,7540.00
```

### Een foto ophalen ✅

De url komt uit *Add a photo*, bijvoorbeeld `/images/b3fef9db-....png`. Foto's zijn zonder inloggen op te halen:

```bash
curl -s -o foto.png -w '%{http_code} %{content_type}\n' localhost:8080/images/b3fef9db-ecab-4b73-bb36-d86b037b5b05.png
# 200 image/png
```

### Een foto uploaden zonder de console ✅

```bash
curl -s -X POST localhost:8080/cars/<id van een auto van jou>/images -H "Authorization: Bearer $TOKEN" -F "file=@tiny.png;type=image/png"
```

### Veel auto's tegelijk, om "More cars..." te zien ✅

De lijsten tonen 20 auto's per pagina. Met zeven extra auto's zie je `Showing 20 of 21.` als je deel I na deel H doet (13 voorbeeldauto's plus de organisatieauto plus zeven) en de optie `More cars...`:

```bash
for i in 1 2 3 4 5 6 7; do
  curl -s -o /dev/null -X POST localhost:8080/cars -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"type\":\"BEV\",\"brand\":\"Demo $i\",\"licensePlate\":\"DEMO-0$i\",\"mileage\":$i,\"rate\":20,\"location\":{\"latitude\":52.0,\"longitude\":5.0},\"doors\":5,\"bodyStyle\":\"Hatchback\",\"modelYear\":2024,\"power\":100,\"transmission\":\"Automatic\",\"color\":\"Blue\",\"capacity\":50,\"consumption\":15}"
done
curl -s -i 'localhost:8080/cars?limit=1' | grep -i x-total-count    # X-Total-Count: 21
```

### Het foutformaat ✅

Elke fout heeft dezelfde vorm:

```bash
curl -s -i localhost:8080/cars/9999 -H "Authorization: Bearer $TOKEN"
# HTTP/1.1 404 Not Found
# {"error":"not_found","message":"Car with ID 9999 not found"}
```

De volledige lijst van endpoints staat in de [README](../README.md#api) en in `resources/openapi/documentation.yaml`.

---

## Problemen oplossen

| Probleem | Oplossing |
|---|---|
| `Cannot connect to the Docker daemon` | Start Docker Desktop en wacht tot `docker info` een "Server Version" toont. |
| `java.net.BindException: Address already in use` bij het starten van de server | Poort 8080 is bezet: er draait al een server (misschien een oude van jezelf). Stop die; `lsof -ti :8080` toont het proces. De bestaande server blijft gewoon werken. |
| Poort 5432 bezet | Een andere Postgres draait. Stop die, of pas `docker-compose.yml` aan en zet `DATABASE_URL` mee. |
| `ERROR: There are several matching application modules in the project. Please specify one with the '--module' option.` | Je liet `-m` weg. Gebruik `-m rent-a-car` voor de server en `-m cli` voor de client. `./kotlin show modules` toont de namen; de servermodule heet naar de map waarin je clonet. |
| `Cannot reach the server at ...` in de client | De server draait niet, of op een ander adres. Start de server in terminal 1 en controleer `curl localhost:8080/cars`. |
| De eerste `./kotlin ...` duurt lang | Normaal: de toolchain en de JDK worden de eerste keer gedownload. |
| `! Your login has expired or was ended elsewhere. Log in again.` | Je token is ingetrokken (bijvoorbeeld door een wachtwoordwijziging elders) of verlopen. De client brengt je terug naar het startscherm: log opnieuw in. |
| `Too many failed login attempts` | Wacht de genoemde tijd, of herstart de server (de teller staat in het geheugen). |
| Een reservering voor vandaag annuleren geeft 409 | Dat klopt als het dagdeel al begonnen is. Gebruik een datum van morgen, of begrijp dat een reservering met een rit nooit geannuleerd kan worden. |
| Een rit starten geeft 409 | Starten kan alleen op de dag van de reservering zelf (Nederlandse datum) en één keer. Een rit achteraf opslaan (`Record a finished trip in one go`) kan voor vandaag of eerder. |
| Gisteren of vandaag lijkt te verschillen rond middernacht | De server gebruikt de Nederlandse datum. Neem bij *Date* gewoon de standaard met Enter. |
| De foto wordt niet geaccepteerd | Alleen echte JPEG of PNG tot 5 MB, maximaal 10 per auto. De client controleert dat vooraf en zegt wat er mis is. |

## Wat niet is getest

- **Windows** (`kotlin.bat`) en **Linux**: alleen macOS is gebruikt. De shell-opdrachten in deze tutorial (`date -v+1d`, `base64 -d`, `printf`) zijn voor macOS en Linux; de macOS-variant van `date` staat erbij.
- **Reserveren rond een dagdeelgrens:** de stappen zijn zo opgezet dat ze op elk tijdstip werken (annuleren met een datum van morgen; een annulering die mislukt, doet dat omdat er een rit is). Het geval "annuleren van een dagdeel dat net begonnen is" heb ik in de scenario's niet apart nagelopen. Dat een rit kan worden gestart op een dagdeel dat nog niet begonnen is (vanavond, terwijl het nu 16:27 is), heb ik wel gezien.
- **Het ruime geval:** een tweede client tegelijk op dezelfde server heb ik alleen gebruikt voor G4. Het gelijktijdig reserveren van hetzelfde dagdeel is in de testsuite van de server gedekt, niet met de console.
- **Bekend gedrag dat je kunt tegenkomen** (niet kapot, wel opvallend):
  - Wie een auto of account verwijdert, verwijdert ook de reserveringen en ritten van huurders (zie H2). Hun bonuspunten blijven.
  - Een dagdeel van vandaag dat al voorbij is, kan nog worden gereserveerd.
  - Een mislukte actie kan een id "verbruiken": nummers in de lijsten springen dan.
  - De client kent bij inloggen je account-id en je organisaties niet (de API heeft geen "wie ben ik" of lijst van organisaties); je typt ze één keer.
  - De fleetrapportage in de console is een tabel; het CSV-formaat zit alleen in de API.

## Checklist

Alles hieronder is in dezelfde sessie uitgevoerd, op een schone database.

| Functie | Menu | Geverifieerd |
|---|---|---|
| Registreren | Register | ✅ |
| Inloggen en uitloggen | Log in / Log out | ✅ |
| Bezoeker: auto's bekijken | Browse as a visitor › Cars | ✅ |
| Filteren (type, tarief, brandstof, enz.) en sorteren | Cars › List and search | ✅ |
| Dichtstbij op afstand | Cars › List and search (latitude, longitude) | ✅ |
| Vrij op een datum en dagdeel | Cars › List and search (Free on date) | ✅ |
| Beschikbaarheid van een auto | Cars › Is a car free? | ✅ |
| Lange lijsten, volgende pagina | List and search, History, Leaderboard | ✅ |
| Auto aanbieden (BEV, ICE, FCEV) | Cars › Offer my car | ✅ |
| Kenteken bestaat al (409) | Cars › Offer my car | ✅ |
| Details met foto's en beoordelingen | Cars › Car details | ✅ |
| Auto aanpassen | Cars › Update a car I manage | ✅ |
| Onderhoud bijhouden | Update, Mark a car as serviced | ✅ |
| Foto toevoegen (en foutmeldingen) | Cars › Add a photo | ✅ |
| Auto verwijderen | Cars › Delete a car I manage | ✅ |
| Andermans auto (403) | Update, Delete, Service, TCO | ✅ |
| Kosten per km | Costs › Energy cost per km | ✅ |
| Offerte voor een rit | Costs › Quote a trip | ✅ |
| Autotypes vergelijken (kosten en CO2) | Costs › Compare car types | ✅ |
| TCO per jaar | Costs › Total cost of ownership | ✅ |
| Reserveren (en 400/409) | Reservations › Reserve a car | ✅ |
| Mijn reserveringen met statusfilter | Reservations › My reservations | ✅ |
| Annuleren (en 409) | Reservations › Cancel | ✅ |
| Rit starten en beëindigen | Trips › Start / End | ✅ |
| Rit in één keer opslaan | Trips › Record in one go | ✅ |
| Bonuspunten | Trips › End / Record | ✅ |
| Ritgeschiedenis | Trips › My trip history | ✅ |
| Ritten van een auto die ik beheer (403 voor anderen) | Trips › Trips with a car I manage | ✅ |
| Beoordelen (en 409) | Trips › Rate the car | ✅ |
| Rijstatistieken | Insights › My driving stats | ✅ |
| Ranglijst | Insights › Leaderboard | ✅ |
| Naam en e-mail wijzigen (400/409) | Account › Update | ✅ |
| Wachtwoord wijzigen (403) | Account › Change my password | ✅ |
| Account verwijderen (en gevolgen) | Account › Delete | ✅ |
| Laatste beheerder bij verwijderen (409) | Account › Delete | ✅ |
| Organisatie aanmaken | Organizations › Create | ✅ |
| Uitnodigen en lid worden (404 bij gebruikte code) | Invite, Join | ✅ |
| Rol wijzigen (403, laatste beheerder 409) | Change a member's role | ✅ |
| Lid verwijderen of weggaan (409 laatste beheerder) | Remove a member, or leave | ✅ |
| Organisatieauto toevoegen (403 voor buitenstaander) | Add an organization car | ✅ |
| Organisatieauto beheren door leden, verwijderen door beheerder | Update, Delete | ✅ |
| Fleetrapport (403 voor buitenstaander) | Fleet report | ✅ |
| Inloggen blokkeren na vijf fouten (429, minuten) | Log in | ✅ |
| Inlog overleeft een herstart | Reservations › My reservations | ✅ |
| Ingetrokken token: terug naar het startscherm | elke actie | ✅ |
| Server niet bereikbaar | elke actie | ✅ |
| `back` annuleert | elke vraag | ✅ |
| Rapport als CSV, foto ophalen, foutformaat, Swagger | curl en `/swagger` | ✅ |
