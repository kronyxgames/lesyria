# Domain and Hosting Setup for Lesyria

Guide to make the server accessible via play.lesyria.com and provide a downloadable launcher.

## Domain Setup

1. **Purchase Domain**: Buy lesyria.com from a registrar like Namecheap, GoDaddy, or OVH.
2. **DNS Configuration**:
   - Set A record for @ pointing to your server's IP address.
   - Optionally, set CNAME for play.lesyria.com to lesyria.com.
3. **SSL Certificate**: Use Let's Encrypt for free SSL (important for security).

## Server Hosting

1. **Choose Provider**: Use a VPS like DigitalOcean, Linode, or dedicated server for performance.
   - Recommended: 8GB RAM, 4 CPU cores, SSD storage.
2. **Static IP**: Ensure your server has a static IP address.
3. **Port Forwarding**:
   - Port 25565 (TCP/UDP) for Java Edition.
   - Port 19132 (UDP) for Bedrock Edition (via GeyserMC).
4. **Firewall**: Configure UFW or iptables to allow only necessary ports.
5. **Monitoring**: Use tools like Prometheus or simple scripts to monitor server health.

## Launcher Distribution

1. **Host Launcher**: Upload the JAR to your website or GitHub Releases.
2. **Website**: Create a simple site (e.g., with GitHub Pages or static hosting) with:
   - Server info
   - Download link for launcher
   - Rules and features
3. **Auto-Updater**: Implement version checking in the launcher for updates.

## Security Considerations

- Use strong passwords and SSH keys.
- Keep server software updated.
- Implement rate limiting and anti-DDoS measures.
- Regular backups of world data.

## Alternatives

- Use Minecraft hosting services like Shockbyte or Apex, but custom domain may cost extra.
- For testing, use free tiers or local hosting first.

## Next Steps

1. Set up domain and DNS.
2. Provision server.
3. Deploy Lesyria server.
4. Test connections.
5. Build and distribute launcher.