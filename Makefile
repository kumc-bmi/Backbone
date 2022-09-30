all: .make.6_run_iteration_example


flink_version=1.15.2
scala_version=2.12
flink_dir=flink-$(flink_version)

.make.1_java_version:
	#You need to have Java 11 installed :  https://nightlies.apache.org/flink/flink-docs-master/docs/try-flink/local_installation/
	java -version

.make.2_download_flink:.make.1_java_version
	wget -q https://dlcdn.apache.org/flink/$(flink_dir)/$(flink_dir)-bin-scala_$(scala_version).tgz
	#wget -q https://dlcdn.apache.org/flink/$(flink_dir)$(flink_dir)-src.tgz
	touch $@

.make.3_install_flink:.make.2_download_flink
	tar -xzf flink-*.tgz
	touch $@

.make.4_start_cluster:.make.stop_cluster
	cd $(flink_dir) && ./bin/start-cluster.sh

.make.5_run_wordcount_example:.make.4_start_cluster
	cd $(flink_dir) && ./bin/flink run examples/streaming/WordCount.jar
	cd $(flink_dir) && tail log/flink-*-taskexecutor-*.out
	touch $@

.make.6_run_iteration_example:.make.5_run_wordcount_example
	cd $(flink_dir) && ./bin/flink run examples/streaming/Iteration.jar
	cd $(flink_dir) && tail log/flink-*-taskexecutor-*.out
	touch $@

.make.7_run_ohnlp:.make.6_run_iteration_exampl
	cd $(flink_dir) && ./bin/flink run 
	cd $(flink_dir) && tail log/flink-*-taskexecutor-*.out
	touch $@

.make.stop_cluster:
	cd $(flink_dir) && ./bin/stop-cluster.sh || true

clean:.make.stop_cluster
	rm .make.* || true
	rm *.tgz* || true
	rm -rf $(flink_dir) || true
