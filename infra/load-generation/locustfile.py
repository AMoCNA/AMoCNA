from locust import HttpUser, constant_pacing, task


class SockShopUser(HttpUser):
    """One HTTP call per second per user so Locust user_count is approx. req/s.

    Browse-only mix so the entry front-end saturates; cart/orders hang under overload
    and leave Locust users stuck even after scale-out.
    """

    # Fail fast + no keep-alive so users reattach to new pods under continuous spike
    # (real clients after scale-out), instead of staying pinned on overloaded connections.
    network_timeout = 2.0
    connection_timeout = 2.0
    wait_time = constant_pacing(1)

    def on_start(self):
        self.client.headers["Connection"] = "close"

    @task(6)
    def browse_home(self):
        self.client.get("/")

    @task(5)
    def view_catalogue(self):
        self.client.get("/catalogue")

    @task(3)
    def view_item(self):
        self.client.get("/catalogue/6d62d909-f953-472e-8a9c-99932ce7ffce")
