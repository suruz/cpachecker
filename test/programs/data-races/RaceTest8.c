/*
 * Copyright (c) 2014-2016 ISPRAS (http://www.ispras.ru)
 * Institute for System Programming of the Russian Academy of Sciences
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * ee the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Test checks, how the tool handle bitwise axioms*/

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/device.h>
#include <linux/mutex.h>
#include <verifier/thread.h>

static DEFINE_MUTEX(my_mutex);

int false_unsafe;

int f(int a) {
	if (a & 11) {
		false_unsafe = 1;
		return false_unsafe;
	}
	return 0;
}

int ldv_main(void* arg) {
	int b = f(0);
	if (b != 0) {
		false_unsafe = 1;
	}
}

static int __init init(void)
{
	struct ldv_thread thread2;

	ldv_thread_create(&thread2, &ldv_main, 0);
	ldv_main(0)
	return 0;
}

module_init(init);