from locust import HttpUser, task, between


class BookInfoUser(HttpUser):
    wait_time = between(0.3, 1.0)

    @task
    def productpage(self):
        self.client.get("/productpage")
