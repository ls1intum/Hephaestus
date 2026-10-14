# Installing Hephaestus

There is exactly **one supported install**: the Docker Compose stack in
[`docker/self-host/`](docker/self-host/) on one 64-bit Linux host.
The [installation requirements](https://docs.hephaestus.build/admin/install#requirements)
describe the supported hosts and capacity guidance.
Other setups may work but are unsupported.

**→ Follow the install guide: <https://docs.hephaestus.build/admin/install>**

For contributor/development setup, see the
[contributor docs](https://docs.hephaestus.build/contributor/local-development) instead.
Contributors need the Node.js version pinned in `package.json` and the Vite+ launcher (`vp`). The
supported repository-based installation also uses the pinned Node.js version to verify signed release
locks.
