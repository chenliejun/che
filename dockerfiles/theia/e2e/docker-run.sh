#!/bin/sh
# Copyright (c) 2018 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0

cd /home/theia/ && bash start.sh&
sleep 5s
cd /home/cypress && ./node_modules/.bin/cypress run
 
