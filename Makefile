all: .make.6_run_iteration_example


flink_version=1.13.6
scala_version=2.12
flink_dir=flink-$(flink_version)
flink_port=8088

.make.1_java_version:
	#You need to have Java 11 installed :  https://nightlies.apache.org/flink/flink-docs-master/docs/try-flink/local_installation/
	java -version

.make.2_download_flink:.make.1_java_version
	wget -q https://archive.apache.org/dist/flink/$(flink_dir)/$(flink_dir)-bin-scala_$(scala_version).tgz
	#wget -q https://archive.apache.org/dist/flink/$(flink_dir)$(flink_dir)-src.tgz
	touch $@

.make.3_install_flink:.make.2_download_flink
	tar -xzf flink-*.tgz
	touch $@

.make.4_install_and_config_flink:.make.3_install_flink
	cd $(flink_dir) &&\
	echo 'localhost:$(flink_port)' > conf/masters &&\
	echo 'jobmanager.web.port: $(flink_port)' >> conf/flink-conf.yaml &&\
	echo 'rest.bind-port: $(flink_port)' >> conf/flink-conf.yaml &&\
	echo 'rest.port: $(flink_port)' >> conf/flink-conf.yaml

.make.5_start_cluster:.make.stop_cluster
	cd $(flink_dir) && ./bin/start-cluster.sh

.make.6_run_iteration_example:.make.5_start_cluster
	cd $(flink_dir) && ./bin/flink run examples/streaming/Iteration.jar
	cd $(flink_dir) && tail log/flink-*-taskexecutor-*.out
	touch $@

.make.7_run_ohnlp:.make.6_run_iteration_exampl
	cd $(flink_dir) && ./bin/flink run 
	cd $(flink_dir) && tail log/flink-*-taskexecutor-*.out
	touch $@

.make.stop_cluster:
	cd $(flink_dir) && ./bin/stop-cluster.sh || true
	#all flink processes- TODO:kill all flink process before starting a new flink process
	ps -ef|grep flink

clean:.make.stop_cluster
	rm .make.* || true
	rm *.tgz* || true
	rm -rf $(flink_dir) || true
