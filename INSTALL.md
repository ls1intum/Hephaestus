# Installing Hephaestus

There is exactly **one supported install**: the Docker Compose stack in
[`docker/self-host/`](docker/self-host/) on one 64-bit Linux host
(4 vCPUs / 8 GB RAM / 40 GB SSD recommended). Other setups may work but are unsupported.

**→ Follow the install guide: <https://ls1intum.github.io/Hephaestus/admin/install>**

For contributor/development setup, see the
[contributor docs](https://ls1intum.github.io/Hephaestus/contributor/local-development) instead.
Contributors need the Node.js and Bun versions pinned in `package.json`. The supported repository-based
installation also uses the pinned Node.js version to verify signed release locks.
