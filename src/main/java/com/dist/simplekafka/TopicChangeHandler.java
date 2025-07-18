package com.dist.simplekafka;

import org.I0Itec.zkclient.IZkChildListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TopicChangeHandler implements IZkChildListener {
    private final ZookeeperClient zookeeperClient;
    private final ZkController controller;
    private Set<String> allTopics = new HashSet<>();

    public TopicChangeHandler(ZookeeperClient zookeeperClient,
                              ZkController controller) {
        this.zookeeperClient = zookeeperClient;
        this.controller = controller;
    }

    public Set<String> getAllTopics() {
        return allTopics;
    }

    @Override  //topic/id/topic1     /topic/id          ["topic1", "topic2]
    public void handleChildChange(String parentPath, List<String> currentChildren) {
        Set<String> newTopics = getNewlyAddedTopics(currentChildren);

        //Assignment connect controller handling of new topic.
        //

        //        getDeletedTopics(currentChildren); //not handling deleted topics as
//        of now.

        allTopics = new HashSet<>(currentChildren);

        newTopics.forEach(topicName -> {
            List<PartitionReplicas> replicas = zookeeperClient.getPartitionAssignmentsFor(topicName);
//            [{0, [1,2,3]}, {1, [2,3,1]}  0 => 1 leader , 1 => 2
            controller.handleNewTopic(topicName, replicas);
        });
    }

    private void getDeletedTopics(List<String> currentChildren) {
        Set<String> deletedTopics = new HashSet<>(allTopics);
        deletedTopics.removeAll(currentChildren);
    }

    private Set<String> getNewlyAddedTopics(List<String> currentChildren) {
        Set<String> newTopics = new HashSet<>(currentChildren);
        newTopics.removeAll(allTopics);
        return newTopics;
    }
}
