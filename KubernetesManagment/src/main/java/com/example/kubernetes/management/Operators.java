package com.example.kubernetes.management;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Operators {

    static Logger logger = LoggerFactory.getLogger(Operators.class);

    private int counter = 0;

    public Operators() {

    }

    @Scheduled(fixedRate = 60000)
    private void runIncrementer() {
        System.out.println(counter++);
        Config config = new ConfigBuilder().build();
        KubernetesClient client = new DefaultKubernetesClient(config);

//        NamespaceList namespaceList = client.namespaces().list();
//        List<Namespace> namespaces = namespaceList.getItems();
////
////        for(Namespace namespace: namespaces) {
////            logger.info("Namespace: ");
////            logger.info(namespace.getMetadata().getName());
////        }
//
//        try {
//            String namespace = client.pods().withName("carts-76dd6bf8f9-fgct9").get().getMetadata().getNamespace();
//            client.pods().inNamespace("sock-shop").withName("carts-76dd6bf8f9-fgct9").delete();
//            System.out.println("TEGO NIE WYPISUJE");
//            logger.info("TEGO NIE WYPISUJES");
//            System.out.println("Namespace of pod: " + namespace);
//            logger.info("Namespace of pod: " + namespace);
//        } catch (NullPointerException e) {
//            System.out.println("no blont");
//        }
//
//        System.out.println("TEGO NIE WYPISUJE");
//        logger.info("TEGO NIE WYPISUJES");
//
////        List<Pod> podList = client.pods().list().getItems();
//        PodList podList = client.pods().inAnyNamespace().list();
//        List<Pod> list = podList.getItems();
//        logger.info("Found " + list.size() + " Pods: ");
//        for(Pod pod: list) {
//            logger.info(String.format(" * %s (namespace %s)", pod.getMetadata().getName(), pod.getMetadata().getNamespace()));
////            client.pods().delete(pod);
//        }
//
//        PodList podsFromSockShop = client.pods().inNamespace("sock-shop").list();
//        List<Pod> list2 = podList.getItems();
//
//        for(Pod pod: list2) {
//            logger.info(String.format(" * %s (namespace22222222222 %s)", pod.getMetadata().getName(), pod.getMetadata().getNamespace()));
//        }

        try {
            logger.info("Czy sie udało: " + client.pods().inNamespace("sock-shop").withName("carts-db-775b544b45-cgwfr").get().getMetadata().getName());
            client.pods().inNamespace("sock-shop").withName("carts-db-775b544b45-cgwfr").delete();
        } catch(NullPointerException e) {
            System.out.println("NIE");
        }

//        try {
//            logger.info("Czy sie udało: " + client.pods().inNamespace("sock-shop").withName("front-end").get().getMetadata().getName());
//            client.pods().inNamespace("sock-shop").withName("front-end").delete();
//        } catch(NullPointerException e) {
//            System.out.println("NIE");
//        }
//
//        try {
//            logger.info("Czy sie udało: " + client.pods().inNamespace("hephaestus").withName("hephaestus-gui").get().getMetadata().getName());
//            client.pods().inNamespace("hephaestus").withName("hephaestus-gui").delete();
//        } catch(NullPointerException e) {
//            System.out.println("NIE");
//        }

    }
}
