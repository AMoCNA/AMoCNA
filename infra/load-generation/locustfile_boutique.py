from locust import HttpUser, constant_pacing, task


class BoutiqueUser(HttpUser):
    """One HTTP call per second per user so Locust user_count is approx. req/s."""

    network_timeout = 2.0
    connection_timeout = 2.0
    wait_time = constant_pacing(1)

    def on_start(self):
        self.client.headers["Connection"] = "close"


    @task(5)
    def home(self):
        self.client.get("/")

    @task(2)
    def cart(self):
        self.client.get("/cart")

    @task(1)
    def products(self):
        self.client.get("/product/OLJCESPC7Z")
