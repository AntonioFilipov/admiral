# CHANGELOG

## 0.9.5-SNAPSHOT

* Add ability to provision and manage Docker hosts on AWS, Azure and vSphere.

* Simplify adding of existing hosts, by auto configure them over SSH. More information [here](https://github.com/vmware/admiral/wiki/User-guide#automatic-configuration-over-ssh)

* Add a new configuration and runtime element - Closure. Ability to execute code in a stateless manner using different programming languages. Integrated in the container template and can be used to tweak the containers and their configuration at provisioning time.

* Redesigned the UI and integrating [Clarity](https://vmware.github.io/clarity/).

* Added UI for listing tags of images.

* Add the administrative Xenon UI

* Communication to the agent is now over SSL.

* Reduced the size of the Admiral agent.

* Reduced memory footprint.

* Various bug fixes and improvements.

* Added support for multiple path segments in the names of repositories, e.g. localhost:5000/category/sub-category/repo-name

## 0.9.1

* Added Admiral CLI, **a command line tool to manage and automate Admiral**. More information [here] (https://github.com/vmware/admiral/blob/master/cli/README.md).

* Groups are now Projects

* (Group Resource) Policies are now (Group Resource) Placements and Resource pools are now Placement Zones.

* Added support for tag-based placement zones.

* Add support for single and multi host user defined application networking using native Docker networking. Support for Docker compose container networking configuration.

  More information [here](https://github.com/vmware/admiral/wiki/User-guide#networking)

* Add UI and Services for managing networks

* Automatic discovery of existing networks

* Improved clustering

* Batch operations on UI list elements

* Enabled encryption of sensitive document properties

* Small usability enhancements and bugfixes

## 0.5.0

* Initial open source release.

Docker hub image: https://hub.docker.com/r/vmware/admiral/ v.0.5.0

All binaries: https://bintray.com/vmware/admiral/admiral#files/com/vmware/admiral

Admiral server: admiral-host-0.5.0-uber-jar-with-agent.jar