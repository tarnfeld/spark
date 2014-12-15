/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.scheduler.cluster.mesos

import org.apache.mesos.Protos.{ContainerInfo, Volume}
import org.apache.mesos.Protos.ContainerInfo.DockerInfo

import org.apache.spark.Logging

/**
 * A collection of utility functions which can be used by both the
 * MesosSchedulerBackend and the CoarseMesosSchedulerBackend.
 */
private[spark] object MesosSchedulerBackendUtil extends Logging {
  /**
   * Parse a volume spec in the form passed to 'docker run -v'
   * which is [host-dir:][container-dir][:rw|ro]
   */
  def parseVolumesSpec(volumes: String): List[Volume] = {
    volumes.split(",").map(_.split(":")).map({ spec: Array[String] =>
        val vol: Volume.Builder = Volume
          .newBuilder()
          .setMode(Volume.Mode.RW)
        spec match {
          case Array(container_path) => 
            Some(vol.setContainerPath(container_path))
          case Array(container_path, "rw") =>
            Some(vol.setContainerPath(container_path))
          case Array(container_path, "ro") =>
            Some(vol.setContainerPath(container_path)
                    .setMode(Volume.Mode.RO))
          case Array(host_path, container_path) => 
            Some(vol.setContainerPath(container_path)
                    .setHostPath(host_path))
          case Array(host_path, container_path, "rw") =>
            Some(vol.setContainerPath(container_path)
                    .setHostPath(host_path))
          case Array(host_path, container_path, "ro") =>
            Some(vol.setContainerPath(container_path)
                    .setHostPath(host_path)
                    .setMode(Volume.Mode.RO))
          case spec => {
            logWarning("parseVolumeSpec: unparseable: " + spec.mkString(":"))
            None
          }
      }
    })
    .filter { _.isDefined }
    .map { _.head.build() }
    .toList
  }

  /**
   * Parse a portmap spec, simmilar to the form passed to 'docker run -p'
   * the form accepted is host_port:container_port[:proto]
   * Note:
   * the docker form is [ip:]host_port:container_port, but the DockerInfo
   * message has no field for 'ip', and instead has a 'protocol' field.
   * Docker itself only appears to support TCP, so this alternative form
   * anticipates the expansion of the docker form to allow for a protocol
   * and leaves open the chance for mesos to begin to accept an 'ip' field
   */
  def parsePortMappingsSpec(portmaps: String): List[DockerInfo.PortMapping] = {
    portmaps.split(",").map(_.split(":")).map({ spec: Array[String] =>
      val portmap: DockerInfo.PortMapping.Builder = DockerInfo.PortMapping
        .newBuilder()
      spec match {
        case Array(host_port, container_port) =>
          Some(portmap.setHostPort(host_port.toInt)
                      .setContainerPort(container_port.toInt))
        case Array(host_port, container_port, protocol) =>
          Some(portmap.setHostPort(host_port.toInt)
                      .setContainerPort(container_port.toInt)
                      .setProtocol(protocol))
        case spec => {
          logWarning("parsePortMappingSpec: unparseable: " + spec.mkString(":"))
          None
        }
      }
    })
    .filter { _.isDefined }
    .map { _.head.build() }
    .toList
  }

  def withDockerInfo(
      container: ContainerInfo.Builder,
      image: String,
      volumes: Option[List[Volume]] = None,
      network: Option[ContainerInfo.DockerInfo.Network] = None,
      portmaps: Option[List[ContainerInfo.DockerInfo.PortMapping]] = None) = {

    val docker = ContainerInfo.DockerInfo.newBuilder().setImage(image)
    network.map(docker.setNetwork _)
    portmaps.map(_.map(docker.addPortMappings _))

    container.setType(ContainerInfo.Type.DOCKER)
    container.setDocker(docker.build())
    volumes.map(_.map(container.addVolumes _))

    logInfo("withDockerInfo: using docker image: " + image)
  }
}
