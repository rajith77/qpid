#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
examplesdir=$(pkgdatadir)/examples/old_api/request-response

MAKELDFLAGS=$(CLIENTFLAGS)
include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=client server

client_SOURCES=client.cpp
client_LDADD=$(CLIENT_LIB) $(COMMON_LIB)

server_SOURCES=server.cpp
server_LDADD=$(CLIENT_LIB) $(COMMON_LIB)

examples_DATA=               \
	server.cpp           \
	client.cpp           \
	$(MAKEDIST)

EXTRA_DIST=                  \
	$(examples_DATA)     \
	CMakeLists.txt       \
	verify               \
	verify.in            \
	request-response_client.vcproj \
	request-response_server.vcproj
