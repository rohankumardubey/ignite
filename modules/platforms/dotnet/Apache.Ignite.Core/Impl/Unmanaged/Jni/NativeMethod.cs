/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

using System;

#pragma warning disable 414  // Unused FuncPtr

namespace Apache.Ignite.Core.Impl.Unmanaged.Jni
{
    using System.Diagnostics.CodeAnalysis;
    using System.Runtime.InteropServices;

    /// <summary>
    /// JNINativeMethod structure for registering Java -> .NET callbacks.
    /// </summary>
    [SuppressMessage("Microsoft.Design", "CA1049:TypesThatOwnNativeResourcesShouldBeDisposable")]
    internal struct NativeMethod
    {
        /// <summary>
        /// Method name, char*.
        /// </summary>
        public IntPtr Name;

        /// <summary>
        /// Method signature, char*.
        /// </summary>
        public IntPtr Signature;

        /// <summary>
        /// Function pointer (from <see cref="Marshal.GetFunctionPointerForDelegate"/>).
        /// </summary>
        // ReSharper disable once NotAccessedField.Global
        public IntPtr FuncPtr;
    }
}
