from locust import HttpUser, task, between

class SockShopUser(HttpUser):
    wait_time = between(0.5, 1.5)

    @task(3)
    def view_catalogue(self):
        self.client.get("/catalogue")

    @task(2)
    def view_item(self):
        self.client.get("/catalogue/6d62d909-f953-472e-8a9c-99932ce7ffce")

    @task(1)
    def add_to_cart(self):
        self.client.post("/cart", json={"id": "6d62d909-f953-472e-8a9c-99932ce7ffce", "quantity": 1})

    @task(20)
    def checkout(self):
        self.client.get("/orders", name="/orders")

    @task(5)
    def browse_home(self):
        self.client.get("/")
