# Datenschutzerklärung

Die Technische Universität München (TUM) betreibt über die Forschungsgruppe Applied Education Technologies (AET) die Hephaestus-Plattform ("Hephaestus" bzw. "die Plattform"). Personenbezogene Daten, die über Hephaestus erhoben werden, werden im Einklang mit der Datenschutz-Grundverordnung (DSGVO), dem Bayerischen Datenschutzgesetz (BayDSG), dem Bayerischen Hochschulinnovationsgesetz (BayHIG) und dem Telekommunikation-Digitale-Dienste-Datenschutz-Gesetz (TDDDG) verarbeitet.

Die folgenden Abschnitte beschreiben Art, Umfang, Zweck und Rechtsgrundlage der Erhebung und Verarbeitung personenbezogener Daten gemäß Art. 13 und Art. 14 DSGVO.

## 1. Verantwortlicher

Verantwortlicher im Sinne des Art. 4 Nr. 7 DSGVO ist:

Technische Universität München  
Postanschrift: Arcisstraße 21, 80333 München, Deutschland  
Telefon: +49 (0)89 289-01  
E-Mail: poststelle(at)tum.de

Die Plattform wird betrieben durch:

Forschungsgruppe Applied Education Technologies (AET)  
Prof. Dr. Stephan Krusche  
Boltzmannstraße 3, 85748 Garching b. München, Deutschland  
E-Mail: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de)

## 2. Datenschutzbeauftragter

Der Datenschutzbeauftragte der Technischen Universität München ist erreichbar unter:

Dr. Fabian Franzen  
Datenschutzbüro, Boltzmannstraße 3, 85748 Garching, Deutschland  
Telefon: +49 (0)89 289-17052  
E-Mail: [beauftragter(at)datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)  
Website: [www.datenschutz.tum.de](https://www.datenschutz.tum.de/datenschutz/der-datenschutzbeauftragte/)

## 3. Zweck und Anwendungsbereich

Hephaestus ist eine **Praxis-orientierte Guidance-Plattform für Softwareprojekte**, die die projektbasierte Lehre im Software-Engineering an der TUM sowie die Software-Entwicklungsarbeit in AET-Forschungsprojekten unterstützt. Für jedes *Projekt* (ein Workspace mit den darin konfigurierten Git-Repositories) beobachtet die Plattform Repository-*Events*, erkennt im Workspace definierte *Practices* in den von den *Contributors* erzeugten *Artifacts* (Pull/Merge Requests, Issues, Reviews, Kommentare), dokumentiert unveränderliche *Findings* (Verdikt, Schweregrad, Evidenz, Begründung) und liefert adaptive *Guidance* an den Contributor zurück — die Pipeline Beobachten → Erkennen → Anleiten → Wachsen. Darüber hinaus kann die Plattform Dashboards, Engagement- und Anerkennungsfunktionen (Leaderboards, Ligen, Achievements — pro Workspace optional), Workspace-Benachrichtigungen, einen dialogbasierten Guidance-Assistenten und automatisierte Practice-Reviews der Pull/Merge Requests der Contributors bereitstellen.

**Findings sind beratend.** Die Plattform trifft *keine* automatisierte Entscheidung mit rechtlicher oder ähnlich erheblicher Wirkung im Sinne des Art. 22 DSGVO. Guidance wird an den Contributor geliefert; die pro Artifact gespeicherten Findings sowie die Aggregationen pro Practice sind für andere Mitglieder desselben Workspace — darunter Workspace-Administratoren, Team Leads und Lehrende — als informative Dashboards sichtbar. Eine Dozentin oder ein Team Lead kann daher von den Findings eines Contributors Kenntnis erlangen, wenn sie/er diese Dashboards konsultiert; eine daraus gezogene Konsequenz (etwa bei individuellem Feedback außerhalb von Hephaestus) ist eine eigenständige Bewertung dieser Person im Rahmen ihres eigenen Verfahrens und keine Entscheidung, die Hephaestus im Sinne des Art. 22 DSGVO träfe. Hephaestus automatisiert weder Benotung, Bewertung, Personalprozesse noch Zugangsentscheidungen; Findings werden in keiner von Hephaestus betriebenen automatisierten Entscheidungs-Pipeline weiterverarbeitet.

Die Nutzung der Plattform erfordert eine Authentifizierung über ein selbst betriebenes Identitäts-Management-System (Keycloak) über einen der für Ihren Workspace freigeschalteten föderierten Identitätsprovider. Auf dem Login-Bildschirm werden ausschließlich die Identitätsprovider angeboten, die für den jeweiligen Workspace aktiviert wurden. Die auf Plattformebene aktivierten Identitätsprovider sind in §4.1 aufgeführt.

### 3.1 Angebundene Quellsysteme

Hephaestus synchronisiert Repository-*Events* und *Artifacts* aus Source-Control-Plattformen, die von der Workspace-Administration konfiguriert werden. Die auf Plattformebene integrierten Quellsysteme sind:

- **GitHub** (github.com, betrieben durch GitHub, Inc. / Microsoft Corp.) — verwendet sowohl als föderierter Identitätsprovider (OAuth) als auch als Quellsystem für Workspaces, deren Repositories auf GitHub gehostet werden.
- **gitlab.lrz.de** — die vom **Leibniz-Rechenzentrum (LRZ) der Bayerischen Akademie der Wissenschaften** (Boltzmannstraße 1, 85748 Garching) betriebene GitLab-Instanz, genutzt von TUM-Lehrveranstaltungen und Forschungsgruppen, die ihre Repositories auf LRZ-Infrastruktur hosten. Hephaestus integriert gitlab.lrz.de sowohl als föderierten Identitätsprovider (OpenID Connect) als auch als Quellsystem. Das LRZ ist das wissenschaftliche Rechenzentrum der BAdW und regulärer IT-Dienstleister für TUM und LMU gemäß § 16 Abs. 1 Satz 2 BayHIG i. V. m. der BAdW-Satzung. Für Datenschutzfragen zur LRZ-Infrastruktur ist der Datenschutzbeauftragte des LRZ unter [datenschutz@lrz.de](mailto:datenschutz@lrz.de) erreichbar. Die Nutzungsbedingungen des LRZ-GitLab erfassen die Nutzung für die Lehre und die nicht-kommerzielle akademische Forschung — genau der Zweck, zu dem Hephaestus gitlab.lrz.de-Daten verarbeitet. LRZ und TUM handeln als getrennte Verantwortliche (Art. 4 Nr. 7 DSGVO) für die Daten, die die jeweilige Körperschaft auf ihrer eigenen Infrastruktur verarbeitet; die Beziehung ist eine öffentlich-rechtliche Kooperation gemäß § 16 Abs. 1 Satz 2 BayHIG i. V. m. der BAdW-Satzung und kein Auftrag im Sinne des Art. 28 DSGVO; Art. 26 DSGVO greift nicht, weil die beiden Körperschaften Zwecke und Mittel nicht gemeinsam festlegen.

### 3.2 Workspace-konfigurierbare Integrationen (Shared-Responsibility-Modell)

Hephaestus arbeitet nach einem **Shared-Responsibility-Modell**, in dem TUM/AET die Plattformebene betreibt und jede Workspace-Administration eine begrenzte Auswahl externer Integrationen für ihren eigenen Workspace konfiguriert:

- **TUM / AET** betreibt die Hephaestus-Plattform selbst — den Orchestrator, die PostgreSQL-Datenbank, das Keycloak-Realm, die Practice-Review-Sandbox, die Zugangskontrolle, Backups, das Incident Response und die vorliegende Datenschutzerklärung.
- **Die Workspace-Administration** (typischerweise ein TUM-Lehrstuhl, eine Dozentin oder ein Forschungsgruppenleiter) entscheidet für den eigenen Workspace über (i) welche Git-Repositories synchronisiert werden (die Datenbasis), (ii) welcher LLM-Anbieter mit welchen Zugangsdaten für die KI-gestützten Funktionen des Workspace genutzt wird, (iii) ob Workspace-Benachrichtigungen an Slack geroutet werden (und damit Salesforce/Slack als weiterer Auftragsverarbeiter hinzugezogen wird), (iv) ob Leaderboards, Ligen und Achievements für die Sichtbarkeit unter Peers aktiviert sind, (v) den Practice-Katalog des Workspace — die Menge an Practices, anhand derer Artifacts bewertet werden, und (vi) ob Practice-Reviews auf neuen Pull/Merge Requests automatisch ausgelöst werden.

Hinsichtlich dieser sechs Entscheidungen legen TUM und die Workspace-Administration Zwecke und Mittel gemeinsam im Sinne des Art. 26 DSGVO fest. Der vorliegende Abschnitt 3.2 stellt das Wesentliche der gemeinsamen Verantwortlichkeit dar, das der betroffenen Person gemäß Art. 26 Abs. 2 Satz 2 DSGVO zur Verfügung gestellt wird. TUM/AET ist die zentrale Anlaufstelle für Betroffenenrechte (siehe §2 und §9); Contributors können sich gleichwohl auch direkt an die Workspace-Administration wenden, insbesondere zu workspace-spezifischen Konfigurationsfragen. Stellt die Institution der Workspace-Administration die LLM-API-Zugangsdaten bereit, so ist diese Institution Verantwortliche für die Übermittlung an den LLM-Anbieter und führt den etwaigen Auftragsverarbeitungsvertrag gemäß Art. 28 DSGVO mit dem Anbieter; die TUM ist nicht Verantwortliche für diese Übermittlung.

## 4. Von uns erhobene und verarbeitete Daten

Die folgenden Abschnitte beschreiben jede Kategorie personenbezogener Daten, die wir verarbeiten, den Zweck, die Rechtsgrundlage und die anwendbare Speicherfrist.

### 4.1 Identitäts- und Authentifizierungsdaten

Um Hephaestus zu nutzen, müssen Sie sich über einen für Ihren Workspace aktivierten externen Identitätsprovider authentifizieren. Die in das Keycloak-Realm von Hephaestus auf Plattformebene föderierten Identitätsprovider sind:

- **GitHub** (github.com) — OAuth 2.0.
- **gitlab.lrz.de** — OpenID Connect, betrieben durch das Leibniz-Rechenzentrum der BAdW. Der OIDC-Flow gegen gitlab.lrz.de wird im selben öffentlich-rechtlichen Kooperationsrahmen wie in §3.1 beschrieben betrieben und ist kein Auftrag im Sinne des Art. 28 DSGVO.

Je nachdem, welchen Identitätsprovider Sie wählen, werden folgende Daten vom Provider an Keycloak und in der Folge an Hephaestus übermittelt:

- Externe Nutzerkennung beim Identitätsprovider (GitHub-User-ID bzw. der von gitlab.lrz.de zurückgelieferte `sub`-Claim)
- Benutzername / Login beim Identitätsprovider
- E-Mail-Adresse (wie vom Identitätsprovider bereitgestellt)
- Vollständiger Name (wie vom Identitätsprovider bereitgestellt)
- Avatar-URL und Profil-URL (soweit vom Provider bereitgestellt)

Sie können in Ihren Profileinstellungen weitere föderierte Identitätsprovider mit einem einzigen Hephaestus-Konto verknüpfen und jederzeit wieder trennen, solange mindestens eine aktive Verknüpfung verbleibt.

**Zweck:** Nutzeridentifikation, Session-Management und Zugangskontrolle.

**Rechtsgrundlage:** Art. 6 Abs. 1 lit. e DSGVO i. V. m. Art. 4 Satz 1 BayHIG und Art. 25 Abs. 1 BayDSG (Wahrnehmung einer im öffentlichen Interesse liegenden Aufgabe — Lehre und Betrieb universitärer IT-Dienste). Für Nutzerinnen und Nutzer, die keine TUM-Angehörigen sind (z. B. externe Open-Source-Contributors), erfolgt die Verarbeitung auf der Grundlage des Art. 6 Abs. 1 lit. b DSGVO (Erfüllung eines Vertrags / Erbringung des durch das Login angeforderten Dienstes).

**Speicherfrist:** Identitätsdaten werden gespeichert, solange Ihr Konto besteht. Sie können Ihr Konto jederzeit in Ihren Profileinstellungen löschen; mit der Löschung werden die Identitätsdaten aus Keycloak und aus der Plattformdatenbank entfernt (siehe Abschnitt 9).

### 4.2 Entwicklungsaktivitätsdaten

Hephaestus synchronisiert Entwicklungsaktivitäten aus Git-Repositories, die von der Workspace-Administration im Workspace konfiguriert wurden. Die Daten werden über die GitHub-API bzw. die GitLab-API von gitlab.lrz.de (siehe §3.1) mittels eines von der Plattformadministration konfigurierten App-Installations- oder Zugangstokens abgerufen. Es werden ausschließlich Repositories synchronisiert, die einem Workspace explizit hinzugefügt wurden. Die synchronisierten Daten umfassen:

- Pull/Merge Requests (Titel, Beschreibung, Zustand, Autor, Reviewer, Labels, Zeitstempel, verknüpfte Commits)
- Issues (Titel, Beschreibung, Zustand, Zuständige, Labels, Zeitstempel)
- Code Reviews (Review-Text, Zustand, Autor)
- Review-Kommentare (Kommentartext, Autor, Dateipfad, Zeilenposition)
- Commit-Metadaten (SHA, Autor-Login, Committer, Nachricht, Zeitstempel)
- Repository-Collaborator- und Team-Mitgliedschaftsmetadaten
- Profilinformationen der Autoren der genannten Objekte (Benutzername, Anzeigename, Avatar-URL, Profil-URL)

**Zweck:** Practice-Erkennung und adaptive Guidance zu Projekt-Artifacts — die Kernfunktionalität der Plattform.

**Rechtsgrundlage:** Art. 6 Abs. 1 lit. e DSGVO i. V. m. Art. 4 Satz 1 BayHIG und Art. 25 Abs. 1 BayDSG (Verarbeitung im Rahmen der öffentlichen Aufgabe zur Lehre und zum Betrieb universitärer IT-Dienste). Die Daten sind bereits auf der ursprünglichen Git-Plattform gegenüber demselben Publikum sichtbar. Für nicht-TUM-Contributors ist die Rechtsgrundlage Art. 6 Abs. 1 lit. b DSGVO.

**Verantwortung der Workspace-Administration:** Die Workspace-Administration ist dafür verantwortlich sicherzustellen, dass die erforderliche Autorisierung auf Seiten des Quellsystems vorliegt (GitHub-App-Installation, GitHub Personal Access Token oder Personal Access Token und Webhook-Shared-Secret für gitlab.lrz.de), und dass die Contributors im Workspace informiert sind, dass ihre Repository-Artifacts in Hephaestus eingelesen werden.

**Speicherfrist:** Synchronisierte Daten werden gespeichert, solange das zugehörige Workspace und Repository in der Plattform konfiguriert sind. Wird ein Repository oder ein Workspace entfernt, werden die zugehörigen Daten gelöscht. Quellseitige Inhalte auf GitHub oder gitlab.lrz.de werden durch diese Aktion *nicht* gelöscht und unterliegen den eigenen Aufbewahrungsregeln der Quellplattform.

### 4.3 Kontoeinstellungen, Benachrichtigungs-E-Mail sowie Engagement & Anerkennung

Hephaestus speichert Kontoeinstellungen und leitet aus Ihrer Entwicklungsaktivität Engagement- und Anerkennungssignale ab, darunter:

- Benutzereinstellungen (Benachrichtigungspräferenzen, UI-Einstellungen)
- Optionale Benachrichtigungs-E-Mail-Adresse (wenn Sie Benachrichtigungen konfigurieren, können Sie eine von Ihrer GitHub-E-Mail abweichende Adresse hinterlegen)
- Workspace-Mitgliedschaften und Rollen
- Anerkennungssignale: Platzierung auf dem Wochen-Leaderboard, Liga-Zuordnung, Achievement-Fortschritt (pro Workspace optional)

Anerkennungssignale sind für andere Mitglieder desselben Workspace sichtbar und aktualisieren sich mit jeder Synchronisation. Zusammenfassungen (Benutzername und Platzierung) können über konfigurierte Benachrichtigungskanäle (z. B. einen Slack-Digest des Workspace) verteilt werden, wenn die Workspace-Administration dies aktiviert hat.

**Zweck:** personalisierte Guidance-Oberflächen, Engagement & Anerkennung im Workspace sowie Benachrichtigungen.

**Rechtsgrundlage:** Art. 6 Abs. 1 lit. e DSGVO i. V. m. Art. 4 Satz 1 BayHIG und Art. 25 Abs. 1 BayDSG für die Engagement- und Anerkennungsfunktionen des Workspace und für die von der Workspace-Administration aktivierten Slack-Digests auf Workspace-Ebene; Art. 6 Abs. 1 lit. a DSGVO (Einwilligung) für die optionale individuelle Benachrichtigungs-E-Mail-Adresse, die Sie in Ihrem Profil hinterlegen (Opt-in durch aktive Handlung, jederzeit in den Profileinstellungen widerruflich).

**Speicherfrist:** solange Ihr Konto besteht. Sie können Ihr Konto jederzeit löschen (siehe Abschnitt 9).

### 4.4 KI-gestützte Funktionen (Guidance-Assistent, Practice-Review)

Wenn KI-gestützte Funktionen für Ihren Workspace aktiv sind, werden die folgenden Daten verarbeitet:

- Ihre Chat-Nachrichten mit dem *Guidance-Assistenten* und die KI-generierten Antworten
- Konversationsverlauf (Threads) und Ihr Feedback zu KI-Antworten (hilfreich / nicht hilfreich)
- Für *Practice-Reviews*: der Diff und der relevante Kontext des zu reviewenden Pull/Merge Requests einschließlich Autor-Benutzername und Commit-Metadaten
- Die daraus resultierenden unveränderlichen *Findings* (Verdikt, Schweregrad, Evidenz, Begründung) sowie die an den Contributor ausgelieferte *Guidance*-Textausgabe

Zur Generierung von Antworten übermittelt die Plattform Ihre Nachrichten — zusammen mit relevantem Kontext wie Code-Ausschnitten oder Review-Daten — an den für Ihren Workspace konfigurierten LLM-Anbieter (siehe §6). Es werden ausschließlich die Nachrichten und der relevante Kontext übertragen; Ihr vollständiges Nutzerprofil wird nicht übertragen. Bei Practice-Reviews wird zusätzlich Quellcode in isolierten, ressourcenbegrenzten Docker-Containern auf AET-Infrastruktur ausgeführt, um vor dem LLM-Aufruf strukturelle Signale abzuleiten. Standardmäßig laufen diese Sandboxes in einem Docker-`--internal`-Netzwerk ohne ausgehende Konnektivität, mit Ausnahme eines eng umgrenzten, jobspezifischen LLM-Proxys, der von AET betrieben wird (DNS-Auflösung deaktiviert; der Proxy authentifiziert Aufrufe mit einem per-Job-Token). Inhalte, die an den LLM-Anbieter übertragen werden, unterliegen den Enterprise-API-Bedingungen des Anbieters (die eine Nutzung der übertragenen Daten zum Modelltraining ausschließen), der allgemeinen Pflicht der Contributors, keine Geheimnisse in versioniertem Repository einzuchecken, sowie der Verantwortung der Workspace-Administration, die in Hephaestus eingebrachten Repositories entsprechend zu wählen.

**Workspace-konfigurierbarer LLM-Anbieter:** Die Wahl des LLM-Anbieters (OpenAI, Microsoft Azure OpenAI oder Anthropic) und die zugehörigen API-Zugangsdaten werden pro Workspace von der Workspace-Administration konfiguriert. Stellt die Institution der Workspace-Administration die API-Zugangsdaten bereit, so ist diese Institution Verantwortliche für die Weiterleitung an den LLM-Anbieter und verantwortlich für den etwaigen Auftragsverarbeitungsvertrag gemäß Art. 28 DSGVO mit dem Anbieter (siehe §3.2).

**Zweck:** adaptive Practice-Guidance für Contributors, die in einem Projekt mitwirken (Practice-Erkennung + Guidance-Generierung).

**Rechtsgrundlage:** Für TUM-Contributors Art. 6 Abs. 1 lit. e DSGVO i. V. m. Art. 4 Satz 1 BayHIG und Art. 25 Abs. 1 BayDSG — KI-gestützte Practice-Guidance ist Teil der Lehraufgabe, zu deren Erfüllung Hephaestus betrieben wird, und die Funktion ist standardmäßig Bestandteil dieses Dienstes. Contributors haben das Recht, **jederzeit gemäß Art. 21 DSGVO zu widersprechen**, indem sie "AI review comments" in ihren Profileinstellungen deaktivieren und/oder den Guidance-Assistenten nicht nutzen; bei Widerspruch stellt die Plattform die Übermittlung neuer Artifacts des Contributors an den LLM-Anbieter ein und generiert keine neuen Findings mehr über ihn. Für nicht-TUM-Contributors ist die Rechtsgrundlage Art. 6 Abs. 1 lit. b DSGVO (durch das Login angeforderter Dienst).

**Speicherfrist:** Konversationen mit dem Guidance-Assistenten werden gespeichert, solange Ihr Konto besteht, und können auf Anfrage gelöscht werden; Practice-Review-Findings werden über die Lebensdauer des Workspace gespeichert und können auf Anfrage früher gelöscht werden. Ein Widerspruch gemäß Art. 21 DSGVO beendet die künftige Verarbeitung, bewirkt aber für sich genommen keine Löschung bereits generierter Findings — hierfür ist ein gesonderter Löschantrag (siehe §9) erforderlich.

**Automatisierte Entscheidungsfindung (Art. 22 DSGVO):** KI-gestützte Funktionen erzeugen ausschließlich Findings und Guidance. Findings und Guidance sind *selbst keine* automatisierten Entscheidungen mit rechtlicher oder ähnlich erheblicher Wirkung im Sinne des Art. 22 DSGVO, und die Plattform verarbeitet sie nicht in einer von Hephaestus betriebenen automatisierten Benotungs-, Bewertungs-, Personal- oder Zugangskontroll-Pipeline weiter. Workspace-Mitglieder — einschließlich Peers, Workspace-Administratoren, Team Leads und Lehrender — können Findings pro Artifact und Aggregationen pro Practice für die Contributors in ihrem Workspace als informative Dashboards einsehen; diese Dashboards informieren die menschliche Urteilsbildung, stellen aber selbst keine Entscheidung im Sinne des Art. 22 DSGVO dar. Eine Dozentin oder ein Dozent, die/der auf Informationen aus diesen Dashboards hin handelt (etwa beim individuellen Feedback), bleibt für diese Entscheidung im Rahmen des eigenen Verfahrens außerhalb von Hephaestus verantwortlich.

### 4.5 Server-Logdaten

Bei jedem Zugriff auf die Plattform erheben und speichern unsere Webserver und der Reverse Proxy vorübergehend die folgenden Informationen in Logdateien:

- IP-Adresse des anfragenden Rechners
- Datum und Uhrzeit der Anfrage
- URL und HTTP-Methode der Anfrage
- HTTP-Statuscode der Antwort
- Übertragenes Datenvolumen
- Browsertyp, Browserversion und Betriebssystem (sofern vom Browser übermittelt)
- Verweisende URL (sofern vom Browser übermittelt)

**Zweck:** Betrieb und Sicherheit der Plattform, einschließlich der Erkennung und Abwehr von Angriffen.

**Rechtsgrundlage:** Art. 6 Abs. 1 lit. e DSGVO i. V. m. Art. 4 Satz 1 BayHIG, Art. 25 Abs. 1 BayDSG und § 8 BayDiG (Sicherheit des universitären IT-Systems als Teil der öffentlichen Aufgabe).

**Speicherfrist:** Logdateien rotieren über Dateigrößen-Limits (bis zu 250 MB je Dienst) und unterliegen einer von AET auf Hostseite betriebenen Aufbewahrungskonfiguration mit einem **harten Maximum von 14 Tagen** im regulären Betrieb. Logs, die eine IP-Adresse enthalten, werden über dieses Fenster hinaus nur dann gespeichert, wenn dies zur Aufklärung eines konkreten, laufenden Sicherheitsvorfalls zwingend erforderlich ist, und werden mit Abschluss des Vorfalls umgehend gelöscht. Logs werden nicht mit anderen Datenquellen zusammengeführt und sind ausschließlich AET-Operatoren zugänglich.

## 5. Cookies und browserseitiger Speicher

Hephaestus verwendet ausschließlich technisch notwendigen browserseitigen Speicher:

- **Keycloak-Session-Cookies:** zwingend erforderlich zur Aufrechterhaltung Ihrer Anmeldung.
- **Theme-Einstellung (`theme` im Local Storage):** merkt sich Ihre Hell-/Dunkelmodus-Auswahl. Enthält keine personenbezogenen Daten.

**Rechtsgrundlage:** § 25 Abs. 2 Nr. 2 TDDDG (zwingend erforderlicher Speicher) i. V. m. Art. 6 Abs. 1 lit. e DSGVO, Art. 4 Satz 1 BayHIG und Art. 25 Abs. 1 BayDSG (öffentlich-rechtlicher Plattformbetrieb; die Theme-Einstellung enthält keine personenbezogenen Daten). Es werden keine einwilligungspflichtigen Analytics- oder Tracking-Cookies eingesetzt.

## 6. Empfänger und Drittdienste

Ihre personenbezogenen Daten können im Zusammenhang mit den in Abschnitt 4 beschriebenen Zwecken den folgenden Empfängern zugänglich sein:

- **AET-Team-Mitglieder** — Plattform-Administratoren und Entwicklerinnen (Betrieb, Wartung, Support).
- **Workspace-Mitglieder** — andere Mitglieder von Workspaces, denen Sie angehören, können Ihren Benutzernamen, Ihren Avatar, die an von Ihnen verfassten Pull/Merge Requests in diesem Workspace angelegten *Findings*, die Aggregationen pro Practice zu Ihnen auf den Workspace-Dashboards sowie Engagement- und Anerkennungssignale (Platzierung, Liga, Achievements) sehen, soweit diese Funktionen für den Workspace aktiviert sind. Workspace-Administratoren, Team Leads und Lehrende desselben Workspace sehen dieselben Informationen für die Contributors, mit denen sie zusammenarbeiten.
- **Leibniz-Rechenzentrum (LRZ) der BAdW** — wenn ein Workspace aus gitlab.lrz.de synchronisiert oder sich ein Contributor über den gitlab.lrz.de-OIDC-Identitätsprovider anmeldet, werden personenbezogene Daten mit LRZ-Infrastruktur in Garching ausgetauscht. LRZ und TUM handeln als getrennte Verantwortliche (Art. 4 Nr. 7 DSGVO) für die Daten, die die jeweilige Körperschaft auf ihrer eigenen Infrastruktur verarbeitet; die Beziehung ist eine öffentlich-rechtliche Kooperation gemäß § 16 Abs. 1 Satz 2 BayHIG i. V. m. der BAdW-Satzung und kein Auftrag im Sinne des Art. 28 DSGVO (§3.1). LRZ-Datenschutzerklärung: [doku.lrz.de/display/PUBLIC/Datenschutzerklaerung](https://doku.lrz.de/display/PUBLIC/Datenschutzerklaerung). LRZ-GitLab-Nutzungsbedingungen: [doku.lrz.de/gitlab-nutzungsrichtlinien-10746021.html](https://doku.lrz.de/gitlab-nutzungsrichtlinien-10746021.html).

Die Plattform nutzt die folgenden Auftragsverarbeiter und Drittdienste. Einige werden nur dann eingebunden, wenn die Workspace-Administration Ihres Workspace die entsprechende Funktion aktiviert hat (siehe §3.2 zum Shared-Responsibility-Modell):

- **GitHub, Inc. / Microsoft Corporation** — Identitätsprovider (OAuth) für Contributors, die sich mit GitHub anmelden; Repository-Datensynchronisation für GitHub-gehostete Workspaces. [Datenschutzerklärung](https://docs.github.com/de/site-policy/privacy-policies/github-general-privacy-statement).
- **LLM-Anbieter pro Workspace** — die KI-gestützten Funktionen jedes Workspace rufen einen der folgenden Anbieter auf, je nach Konfiguration der Workspace-Administration: **OpenAI, L.P.** (Enterprise-API, Nicht-Trainings-Bedingungen — [Datenschutzerklärung](https://openai.com/policies/privacy-policy)); **Microsoft Corporation (Azure OpenAI Service)**, regionsseitig konfigurierbar, EU-Region-Deployments verarbeiten innerhalb der EU — [Datenschutzerklärung](https://privacy.microsoft.com/privacystatement); **Anthropic, PBC** (Claude-API unter Enterprise-Nicht-Trainings-Bedingungen) — [Datenschutzerklärung](https://www.anthropic.com/legal/privacy). Nur der für Ihren Workspace konfigurierte Anbieter erhält Ihre KI-Interaktionen.
- **Salesforce, Inc. / Slack Technologies, LLC** — Workspace-Benachrichtigungen und Engagement-/Anerkennungs-Digests *ausschließlich, wenn Ihre Workspace-Administration Slack für den Workspace aktiviert hat*. [Datenschutzerklärung](https://slack.com/trust/privacy/privacy-policy).
- **Durch AET betriebene SMTP-Infrastruktur** über das TUM-Mail-Relay — E-Mail-Zustellung (einschließlich Keycloak-Konto-Verifizierung und Passwort-Reset-Nachrichten sowie optionaler Benachrichtigungs-E-Mails, die Contributors konfigurieren). Keine Übermittlung außerhalb von TUM/AET.

Soweit die Beziehung mit einem der vorgenannten Empfänger Art. 28 DSGVO unterfällt, liegt ein Auftragsverarbeitungsvertrag (AVV) auf Ebene von TUM/AET für die von TUM/AET betriebenen Integrationen vor (GitHub, die von der TUM betriebene LLM-Tenancy, Slack und das TUM-SMTP-Relay); für LLM-Übermittlungen mit von der Institution der Workspace-Administration bereitgestellten Zugangsdaten wird der AVV auf Ebene dieser Institution geführt (§3.2, §4.4). Die Beziehung zum LRZ ist *kein* Auftrag im Sinne des Art. 28 DSGVO, sondern eine öffentlich-rechtliche Kooperation gemäß § 16 Abs. 1 Satz 2 BayHIG i. V. m. der BAdW-Satzung.

## 7. Drittlandsübermittlungen

Die Kern-Plattforminfrastruktur (Application-Server, Datenbank, Keycloak) wird auf von AET an der TUM in Deutschland betriebenen Servern betrieben. Das Quellsystem gitlab.lrz.de läuft auf LRZ-Infrastruktur in Garching, Deutschland — keine Drittlandsübermittlung. Die folgenden Drittdienste sind in den Vereinigten Staaten ansässig; Übermittlungen an sie sind durch die unten aufgeführten Garantien geschützt. Ist der Empfänger für die jeweilige Datenkategorie nach dem EU-U.S. Data Privacy Framework zertifiziert, so ist die primäre Garantie der Angemessenheitsbeschluss der Kommission gemäß Art. 45 Abs. 3 DSGVO in Bezug auf das DPF. Für jegliche Verarbeitung, die nicht von der DPF-Zertifizierung des Empfängers erfasst ist, dienen Standardvertragsklauseln gemäß Art. 46 Abs. 2 lit. c DSGVO als Auffang-Garantie:

- **GitHub, Inc. / Microsoft Corporation** — DPF-zertifiziert; Standardvertragsklauseln als Auffang-Garantie. Azure OpenAI in einer europäischen Region verarbeitet innerhalb der EU.
- **OpenAI, L.P.** — DPF-zertifiziert; Standardvertragsklauseln als Auffang-Garantie.
- **Anthropic, PBC** — DPF-zertifiziert; Standardvertragsklauseln als Auffang-Garantie (nur eingebunden, wenn die Workspace-Administration Anthropic als LLM-Anbieter gewählt hat).
- **Salesforce, Inc. (Slack)** — DPF-zertifiziert; Standardvertragsklauseln als Auffang-Garantie.

Es werden keine personenbezogenen Daten ohne geeignete Garantien nach Kapitel V DSGVO in Drittländer übermittelt.

## 8. SSL/TLS-Verschlüsselung

Hephaestus verwendet für alle Verbindungen eine SSL/TLS-Verschlüsselung. Eine verschlüsselte Verbindung erkennen Sie am Präfix "https://" und am Schloss-Symbol in Ihrem Browser.

## 9. Ihre Rechte

Gemäß der DSGVO haben Sie in Bezug auf Ihre personenbezogenen Daten die folgenden Rechte:

- **Auskunft (Art. 15 DSGVO):** Sie können Auskunft über die zu Ihnen gespeicherten personenbezogenen Daten verlangen.
- **Berichtigung (Art. 16 DSGVO):** Sie können die Berichtigung unrichtiger Daten verlangen.
- **Löschung (Art. 17 DSGVO):** Sie können die Löschung Ihrer personenbezogenen Daten verlangen, vorbehaltlich gesetzlicher Aufbewahrungspflichten. Sie können Ihr Konto jederzeit über die Plattform-Einstellungen löschen; dadurch werden Ihr Profil, Ihre Präferenzen, Ihre Guidance-Assistenten-Konversationen, die zu Ihnen angelegten Findings sowie die Verknüpfung zu Ihren föderierten Identitätsprovidern aus Keycloak und aus der Hephaestus-Datenbank entfernt. Von Ihnen auf GitHub oder gitlab.lrz.de eingestellte Inhalte (Commits, Pull/Merge Requests, Issues) werden durch diese Aktion *nicht* auf den Quellsystemen gelöscht — dort müssen Sie sie gesondert löschen. Prompts, die vor Ihrer Löschung an den LLM-Anbieter übermittelt wurden, unterliegen dem vom Anbieter geführten Aufbewahrungsfenster (bis zu 30 Tage bei der standardmäßigen Enterprise-Abuse-Monitoring-Konfiguration, kürzer, wenn für den Workspace Zero Data Retention mit dem Anbieter vereinbart wurde), bevor der Anbieter sie löscht; nach Ablauf dieses Fensters sind sie Ihrem gelöschten Konto nicht mehr zugeordnet. Das LRZ hält unabhängig von Hephaestus ein eigenes Backup-/Sichtbarkeitsfenster von bis zu sechs Monaten für gelöschte gitlab.lrz.de-Inhalte auf seiner Infrastruktur vor.
- **Einschränkung der Verarbeitung (Art. 18 DSGVO):** Sie können unter bestimmten Voraussetzungen die Einschränkung der Verarbeitung verlangen.
- **Datenübertragbarkeit (Art. 20 DSGVO):** Sie können die Daten, die Sie bereitgestellt haben, in einem strukturierten, maschinenlesbaren Format erhalten.
- **Widerspruch (Art. 21 DSGVO):** Sie können der Verarbeitung, die auf Art. 6 Abs. 1 lit. e DSGVO gestützt ist, widersprechen. Widersprechen Sie, stellt TUM/AET die entsprechende Verarbeitung ein, es sei denn, es können zwingende schutzwürdige Gründe für die Verarbeitung nachgewiesen werden.
- **Widerruf der Einwilligung (Art. 7 Abs. 3 DSGVO):** Soweit die Verarbeitung auf einer Einwilligung beruht (Abschnitt 4.3, optionale Benachrichtigungs-E-Mail), können Sie Ihre Einwilligung jederzeit widerrufen. Der Widerruf berührt die Rechtmäßigkeit der bis dahin erfolgten Verarbeitung nicht.

Zur Ausübung dieser Rechte wenden Sie sich bitte an [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de) oder an den Datenschutzbeauftragten der TUM (siehe Abschnitt 2).

## 10. Beschwerderecht

Unbeschadet anderer Rechtsbehelfe haben Sie das Recht auf Beschwerde bei einer Aufsichtsbehörde (Art. 77 DSGVO). Die für die TUM zuständige Aufsichtsbehörde ist:

Der Bayerische Landesbeauftragte für den Datenschutz (BayLfD)  
Wagmüllerstraße 18, 80538 München, Deutschland  
Telefon: +49 (0)89 212672-0  
E-Mail: [poststelle@datenschutz-bayern.de](mailto:poststelle@datenschutz-bayern.de)  
Website: [https://www.datenschutz-bayern.de](https://www.datenschutz-bayern.de)

## 11. Pflicht zur Bereitstellung von Daten

Die Bereitstellung personenbezogener Daten ist weder gesetzlich noch vertraglich vorgeschrieben. Eine Nutzung der Plattform ist jedoch ohne Authentifizierung über einen für Ihren Workspace konfigurierten föderierten Identitätsprovider (siehe §4.1) nicht möglich, was die Übermittlung der dort beschriebenen Identitätsdaten erfordert. Sind in Ihrem Workspace KI-gestützte Funktionen aktiv, ist die Übermittlung Ihrer Artifacts an den für diesen Workspace konfigurierten LLM-Anbieter (§4.4) Teil der Lehrfunktion der Plattform; Sie können jederzeit gemäß Art. 21 DSGVO widersprechen, die Plattform stellt dann die Übermittlung Ihrer neuen Artifacts an den LLM-Anbieter ein.

## 12. Änderungen dieser Datenschutzerklärung

Die TUM kann diese Datenschutzerklärung von Zeit zu Zeit aktualisieren, um Änderungen in der Datenverarbeitung oder rechtliche Anforderungen nachzuvollziehen. Die aktuelle Fassung ist abrufbar unter [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy).

## 13. Sicherheit der E-Mail-Kommunikation

E-Mail-Adressen, über die Sie TUM/AET zu der Plattform kontaktieren, werden ausschließlich für die entsprechende Korrespondenz genutzt. Die übliche E-Mail-Übertragung kann Sicherheitslücken aufweisen; ein vollständiger Schutz der Daten vor dem Zugriff Dritter während der E-Mail-Übertragung kann nicht gewährleistet werden.
