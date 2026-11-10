# Different JavaFX HMI screens on different PCs

## Architecture

The portal stores independent **screen layouts** and assigns one layout to each enrolled **workstation**. All Windows PCs install the **same** signed JavaFX application once. The app downloads its assigned screen and re-renders from that screen's JSON, enabling custom configurations per PC **without generating bespoke executables**.

### Portal editor
1. In the local prototype, start the portal, set strong admin/viewer passwords, and sign in as `studio-admin`.
2. Open **HMI screens & PCs**. Select the sample overview or create a new screen.
3. Choose screen title and 1–4 columns; add read-only Text, Value, Gauge and Status widgets.
4. Bind each data widget to an existing configured OPC UA tag by its alias; reorder with Up/Down.
5. Save the layout; the server increments the layout revision. Edits are checked against the allowed widget schema and existing tags.
6. Register a PC with a unique ID and name, assign a screen, and save its **one-time secret**. The backend retains only a bcrypt hash.
7. On the JavaFX client PC, enter portal address, viewer account credentials, PC ID and enrollment secret. The client fetches that PC's screen and scoped synthetic telemetry.
8. Reassign the PC to a different screen or edit its assigned screen. While connected, the client retrieves the new definition automatically on its next successful poll.

### Security and limitations
- The portal and HMI are a **loopback-only simulated prototype**. No active cloud deployment, production-grade device identity, OPC UA subscription, installer or signed layout package exists yet.
- A workstation ID by itself is not trusted. The server verifies the PC's one-time secret and separate viewer credentials before serving a screen or readings.
- Device enrollment tokens are never returned by the list endpoint. There is **no rotation/reset UI yet**; disable and register a new PC ID for the prototype.
- Screen definitions are **data**, not compiled Java, HTML, FXML, scripts or CSS. The JavaFX renderer supports only four fixed safe widget types.
- A shared read-only data credential is **not sufficient for production**. Use per-device mTLS identities, SSO/MFA for operators, organization/site scoping, auditing, certificate/secret lifecycle, and approved deployments.
- Changing a screen is not the same as updating the binary. The release channel and MSI should be uniform across devices; screen assignments are downloaded separately.
- Losing connectivity clears live readings. Future offline support may cache the layout only, but must never show stale process values as current.
- All telemetry currently has **SIMULATED** quality and is not appropriate for industrial operation, alarm response, safety systems, or equipment actuation.

### Future visual designer
A full drag-and-drop editor should add widget resize/coordinates, multi-screen navigation, reusable templates, multi-monitor and kiosk layouts, historian trend/chart widgets, symbols/P&ID elements, tag browser, theme system and role-based control widgets *only after safety review*. A JSON schema and staged/published revisions are preferable to generating native JavaFX source at runtime.
