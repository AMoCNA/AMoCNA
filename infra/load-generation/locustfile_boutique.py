from locust import HttpUser, task, between


class BoutiqueUser(HttpUser):
    wait_time = between(0.3, 1.0)

    @task(5)
    def home(self):
        self.client.get("/")

    @task(2)
    def cart(self):
        self.client.get("/cart")

    @task(1)
    def products(self):
        self.client.get("/product/OLJCESPC7Z")
