import subprocess, os

os.chdir("/home/devbox/test-backend")
env = os.environ.copy()
with open(".env") as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            k, v = line.split("=", 1)
            v = v.strip().strip('"').strip("'")
            env[k] = v

subprocess.run(["java", "-jar", "target/backend-0.0.1-SNAPSHOT.jar", "--server.port=8082"], env=env)
