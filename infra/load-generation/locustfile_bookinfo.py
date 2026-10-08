from locust import HttpUser, constant_pacing, task


class BookInfoUser(HttpUser):
    """One HTTP call per second per user so Locust user_count is approx. req/s."""

    network_timeout = 2.0
    connection_timeout = 2.0
    wait_time = constant_pacing(1)

    def on_start(self):
        self.client.headers["Connection"] = "close"


    @task
    def productpage(self):
        self.client.get("/productpage")
